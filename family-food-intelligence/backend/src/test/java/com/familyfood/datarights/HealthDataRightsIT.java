package com.familyfood.datarights;

import com.familyfood.datarights.application.DataRightsService;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Export and erasure of the health record (NFR-09, AC-9.2, AC-9.3; D-042 defect 4) against a
 * real database.
 *
 * <p>Before W1, account and child erasure left an erased member's observations, goals, habits,
 * meal portions and sharing grants behind — erasure scrubs the {@code household_member} row
 * rather than deleting it (D-028), so none of the {@code ON DELETE CASCADE}s ever fired — and
 * the export omitted all of them. These tests pin both halves, and the two invariants that make
 * erasure safe in a family: nothing of another member is erased (a family meal survives minus
 * the erased share, and the other diner's own derived nutrition with it), and {@code
 * consent_record}/{@code audit_event} are never touched.
 *
 * <p>Everything is set up through the real HTTP endpoints, with the consent each write needs
 * (D-048), so the rows being erased are the rows the product actually writes. Hard erasure is
 * triggered through {@link DataRightsService#performFullErasure} directly, as {@code
 * AccountErasureIT} does, rather than waiting out the grace period.
 */
class HealthDataRightsIT extends IntegrationTestBase {

    /** V14's default household zone; habit days are household days (D-053). */
    private static final ZoneId HOUSEHOLD_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataRightsService dataRights;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    /**
     * Two adults in one household, both tracking. {@code erased} holds one of everything the
     * health modules store; {@code other} shares a family meal with them (and has confirmed
     * their own share, so has derived nutrition from it), records their own water, and shares
     * HYDRATION with {@code erased} — a grant made <em>to</em> the member being erased.
     */
    private record TwoAdults(Session erased, UUID householdId, UUID erasedMemberId,
                             Session other, UUID otherMemberId, UUID otherObservationId,
                             UUID habitId, LocalDate habitDay,
                             UUID soloMealId, UUID familyMealId, UUID otherPortionId,
                             UUID grantByErased, UUID grantToErased) {}

    // ======================================================================== (a) export

    @Test
    @DisplayName("an adult's export carries their observations, goals, habits, meals (own share only) and sharing — nobody else's")
    void exportCarriesTheAdultsOwnHealthRecordAndNoOneElses() throws Exception {
        TwoAdults f = twoTrackingAdults();

        MvcResult result = mockMvc.perform(get("/api/v1/account/export")
                        .header("Authorization", f.erased().authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.households.length()").value(1))
                .andReturn();
        String exportJson = result.getResponse().getContentAsString();
        JsonNode health = objectMapper.readTree(exportJson).get("households").get(0).get("health");
        String self = f.erasedMemberId().toString();

        // Observations: every one is the caller's own. The two typed in, plus what the two
        // meals derived for the caller's share.
        JsonNode observations = health.get("observations");
        assertThat(observations).isNotEmpty();
        observations.forEach(o -> assertThat(o.get("subjectMemberId").asText()).isEqualTo(self));
        assertThat(codesWithSource(observations, "MANUAL")).containsExactlyInAnyOrder("WATER_ML", "WEIGHT_KG");
        assertThat(valuesOf(observations, "WEIGHT_KG", "MANUAL"))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("72.5"));
        // Solo poha 200 g at 180 kcal/100 g, all of it: 360. Family dal 400 g at 120 kcal/100 g,
        // half of it: 240. The other adult's 240 is theirs and is not here.
        assertThat(valuesOf(observations, "ENERGY_KCAL", "DERIVED"))
                .usingElementComparator(BigDecimal::compareTo)
                .containsExactlyInAnyOrder(new BigDecimal("360"), new BigDecimal("240"));

        // Goals and habits, with the day the habit was done.
        assertThat(health.get("goals")).hasSize(1);
        assertThat(health.get("goals").get(0).get("metricCode").asText()).isEqualTo("WATER_ML");
        assertThat(health.get("goals").get(0).get("subjectMemberId").asText()).isEqualTo(self);
        assertThat(health.get("habits")).hasSize(1);
        assertThat(health.get("habits").get(0).get("habit").get("id").asText()).isEqualTo(f.habitId().toString());
        assertThat(health.get("habits").get(0).get("habit").get("name").asText()).isEqualTo("Evening walk");
        assertThat(texts(health.get("habits").get(0).get("completedOn"))).containsExactly(f.habitDay().toString());

        // Meals: both, each carrying the caller's share and only the caller's share.
        JsonNode meals = health.get("meals");
        assertThat(texts(meals, "id")).containsExactlyInAnyOrder(
                f.soloMealId().toString(), f.familyMealId().toString());
        for (JsonNode meal : meals) {
            assertThat(meal.get("portions")).hasSize(1);
            assertThat(meal.get("portions").get(0).get("householdMemberId").asText()).isEqualTo(self);
            assertThat(meal.get("portions").get(0).get("nutritionWithheld").asBoolean()).isFalse();
        }
        JsonNode familyMeal = findById(meals, f.familyMealId());
        assertThat(new BigDecimal(familyMeal.get("portions").get(0).get("nutrition").get("energyKcal").asText()))
                .isEqualByComparingTo("240");

        // Sharing: what the caller shared, and not what was shared with them.
        JsonNode grants = health.get("sharingGrants");
        assertThat(grants).hasSize(1);
        assertThat(grants.get(0).get("id").asText()).isEqualTo(f.grantByErased().toString());
        assertThat(grants.get(0).get("subjectMemberId").asText()).isEqualTo(self);
        assertThat(grants.get(0).get("audienceMemberId").asText()).isEqualTo(f.otherMemberId().toString());
        assertThat(grants.get(0).get("scope").asText()).isEqualTo("ACTIVITY");
        assertThat(grants.get(0).get("level").asText()).isEqualTo("VIEW");

        // Nothing of the other adult anywhere in the document: not their water reading (which
        // they share with the caller), not their portion of the family meal, not the grant they
        // made. Checked on the raw JSON so no section can smuggle one in.
        assertThat(exportJson)
                .doesNotContain(f.otherObservationId().toString())
                .doesNotContain(f.otherPortionId().toString())
                .doesNotContain(f.grantToErased().toString());

        // The same holds the other way round: the other adult's export shows the family meal
        // with their share alone, and not the caller's solo meal.
        MvcResult otherResult = mockMvc.perform(get("/api/v1/account/export")
                        .header("Authorization", f.other().authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode otherHealth = objectMapper.readTree(otherResult.getResponse().getContentAsString())
                .get("households").get(0).get("health");
        assertThat(texts(otherHealth.get("meals"), "id")).containsExactly(f.familyMealId().toString());
        assertThat(otherHealth.get("meals").get(0).get("portions")).hasSize(1);
        assertThat(otherHealth.get("meals").get(0).get("portions").get(0).get("id").asText())
                .isEqualTo(f.otherPortionId().toString());
        otherHealth.get("observations").forEach(o ->
                assertThat(o.get("subjectMemberId").asText()).isEqualTo(f.otherMemberId().toString()));
        assertThat(texts(otherHealth.get("sharingGrants"), "id")).containsExactly(f.grantToErased().toString());
    }

    // ====================================================================== (b) erasure

    @Test
    @DisplayName("account erasure removes the adult's whole health record and nothing of the other adult's")
    void accountErasureRemovesTheHealthRecordAndSparesTheOtherAdult() throws Exception {
        TwoAdults f = twoTrackingAdults();
        UUID erased = f.erasedMemberId();
        UUID other = f.otherMemberId();

        // The erased member really has a row in every table before erasure, so each zero below
        // is an erasure and not an empty start.
        assertThat(count("select count(*) from health_observation where household_member_id = ?", erased)).isPositive();
        assertThat(count("select count(*) from wellness_goal where household_member_id = ?", erased)).isEqualTo(1);
        assertThat(count("select count(*) from habit where household_member_id = ?", erased)).isEqualTo(1);
        assertThat(count("select count(*) from habit_completion where habit_id = ?", f.habitId())).isEqualTo(1);
        assertThat(count("select count(*) from meal_portion where household_member_id = ?", erased)).isEqualTo(2);
        assertThat(count("select count(*) from health_access_grant where subject_member_id = ?", erased)).isEqualTo(1);

        int otherObservationsBefore = count(
                "select count(*) from health_observation where household_member_id = ?", other);
        int otherDerivedFromFamilyMeal = count(
                "select count(*) from health_observation where household_member_id = ? and source_ref = ?",
                other, f.familyMealId());
        assertThat(otherDerivedFromFamilyMeal).isPositive();
        int familyMealItemsBefore = count("select count(*) from meal_item where meal_id = ?", f.familyMealId());
        int consentBefore = count(
                "select count(*) from consent_record where subject_member_id in (?, ?)", erased, other);
        int auditBefore = count("select count(*) from audit_event");

        dataRights.performFullErasure(f.erased().accountId());

        // Everything the erased member's own record held is gone.
        assertThat(count("select count(*) from health_observation where household_member_id = ?", erased)).isZero();
        assertThat(count("select count(*) from wellness_goal where household_member_id = ?", erased)).isZero();
        assertThat(count("select count(*) from habit where household_member_id = ?", erased)).isZero();
        assertThat(count("select count(*) from habit_completion where habit_id = ?", f.habitId())).isZero();
        assertThat(count("select count(*) from meal_portion where household_member_id = ?", erased)).isZero();

        // A meal only they ate records nothing for anyone now, and goes entirely.
        assertThat(count("select count(*) from meal where id = ?", f.soloMealId())).isZero();
        assertThat(count("select count(*) from meal_item where meal_id = ?", f.soloMealId())).isZero();

        // The family meal is the other adult's record too: it survives, with their share only,
        // and their own derived nutrition from it is untouched.
        assertThat(count("select count(*) from meal where id = ?", f.familyMealId())).isEqualTo(1);
        assertThat(count("select count(*) from meal_item where meal_id = ?", f.familyMealId()))
                .isEqualTo(familyMealItemsBefore);
        assertThat(jdbc.queryForList("select household_member_id from meal_portion where meal_id = ?",
                UUID.class, f.familyMealId())).containsExactly(other);
        assertThat(count("select count(*) from health_observation where household_member_id = ? and source_ref = ?",
                other, f.familyMealId())).isEqualTo(otherDerivedFromFamilyMeal);
        assertThat(count("select count(*) from health_observation where household_member_id = ?", other))
                .isEqualTo(otherObservationsBefore);
        assertThat(count("select count(*) from health_observation where id = ?", f.otherObservationId())).isEqualTo(1);

        // ...and still readable by them through the API.
        mockMvc.perform(get("/api/v1/nutrition/meals/{id}", f.familyMealId())
                        .header("Authorization", f.other().authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.portions.length()").value(1))
                .andExpect(jsonPath("$.portions[0].householdMemberId").value(other.toString()));

        // Sharing: what they shared is gone; what was shared with them is revoked, not deleted,
        // because it is the other adult's sharing history.
        assertThat(count("select count(*) from health_access_grant where subject_member_id = ?", erased)).isZero();
        assertThat(count("select count(*) from health_access_grant where id = ?", f.grantByErased())).isZero();
        assertThat(jdbc.queryForObject("select revoked_at is not null from health_access_grant where id = ?",
                Boolean.class, f.grantToErased())).isTrue();
        assertThat(count("select count(*) from health_access_grant where audience_member_id = ? and revoked_at is null",
                erased)).isZero();

        // Evidence survives (AC-9.3): not one consent row or audit row is lost.
        assertThat(count("select count(*) from consent_record where subject_member_id in (?, ?)", erased, other))
                .isEqualTo(consentBefore);
        assertThat(count("select count(*) from audit_event")).isGreaterThanOrEqualTo(auditBefore);
        assertThat(count("select count(*) from audit_event where action = 'DATA_ERASURE_COMPLETED' and resource_id = ?",
                f.erased().accountId())).isEqualTo(1);
    }

    // ================================================================ (c) child erasure

    @Test
    @DisplayName("child erasure removes the child's observations, goals, habits and meal shares, and nothing of the guardian's")
    void childErasureRemovesTheChildsHealthRecordAndSparesTheGuardian() throws Exception {
        Session guardian = registerNewAccount();
        UUID householdId = createHousehold(guardian, "Child Health Household", "Parent");
        UUID guardianMemberId = ownMemberId(guardian, householdId);
        setProfile(guardian, householdId, guardianMemberId, "A31_45");
        grantConsent(guardian, guardianMemberId, "HEALTH_TRACKING");

        UUID childId = addChild(guardian, householdId, "Kiddo");
        setProfile(guardian, householdId, childId, "C4_6");
        grantConsent(guardian, childId, "CHILD_WELLNESS_TRACKING");

        // The guardian's own record, which must come through untouched.
        recordObservation(guardian, guardianMemberId, "WATER_ML", "1500");
        createGoal(guardian, guardianMemberId, "WATER_ML", "AT_LEAST", "2500");
        UUID guardianHabit = createHabit(guardian, guardianMemberId, "Morning stretch");
        LocalDate day = LocalDate.now(HOUSEHOLD_ZONE).minusDays(1);
        completeHabit(guardian, guardianHabit, day);

        // The child's record, guardian-entered (D-048), within D-047 (no nutrition or body goal).
        recordObservation(guardian, childId, "WATER_ML", "400");
        recordObservation(guardian, childId, "SLEEP_MINUTES", "600");
        createGoal(guardian, childId, "SLEEP_MINUTES", "AT_LEAST", "600");
        UUID childHabit = createHabit(guardian, childId, "Fruit at breakfast");
        completeHabit(guardian, childHabit, day);

        // A family dinner both ate (both derive: self, and a child the logger guards), and a
        // snack only the child had.
        Map<UUID, String> dinnerPortions = new LinkedHashMap<>();
        dinnerPortions.put(guardianMemberId, "1.0");
        dinnerPortions.put(childId, "0.5");
        JsonNode dinner = logMeal(guardian, householdId, "DINNER", "Khichdi", "300", "110", dinnerPortions);
        UUID dinnerId = UUID.fromString(dinner.get("id").asText());
        JsonNode snack = logMeal(guardian, householdId, "SNACK", "Banana", "100", "90", Map.of(childId, "1.0"));
        UUID snackId = UUID.fromString(snack.get("id").asText());

        // Before erasure, the guardian's export carries the child's record — the child's share
        // of each meal only — and keeps it apart from the guardian's own.
        MvcResult exportResult = mockMvc.perform(get("/api/v1/account/export")
                        .header("Authorization", guardian.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode household = objectMapper.readTree(exportResult.getResponse().getContentAsString())
                .get("households").get(0);
        household.get("health").get("observations").forEach(o ->
                assertThat(o.get("subjectMemberId").asText()).isEqualTo(guardianMemberId.toString()));
        JsonNode childHealth = household.get("guardedChildren").get(0).get("health");
        childHealth.get("observations").forEach(o ->
                assertThat(o.get("subjectMemberId").asText()).isEqualTo(childId.toString()));
        assertThat(codesWithSource(childHealth.get("observations"), "MANUAL"))
                .containsExactlyInAnyOrder("WATER_ML", "SLEEP_MINUTES");
        assertThat(childHealth.get("goals")).hasSize(1);
        assertThat(childHealth.get("habits")).hasSize(1);
        assertThat(texts(childHealth.get("habits").get(0).get("completedOn"))).containsExactly(day.toString());
        assertThat(texts(childHealth.get("meals"), "id"))
                .containsExactlyInAnyOrder(dinnerId.toString(), snackId.toString());
        for (JsonNode meal : childHealth.get("meals")) {
            assertThat(meal.get("portions")).hasSize(1);
            assertThat(meal.get("portions").get(0).get("householdMemberId").asText()).isEqualTo(childId.toString());
        }
        assertThat(childHealth.get("sharingGrants")).isEmpty();

        int guardianObservationsBefore = count(
                "select count(*) from health_observation where household_member_id = ?", guardianMemberId);
        int guardianDerivedFromDinner = count(
                "select count(*) from health_observation where household_member_id = ? and source_ref = ?",
                guardianMemberId, dinnerId);
        assertThat(guardianDerivedFromDinner).isPositive();
        assertThat(count("select count(*) from health_observation where household_member_id = ?", childId)).isPositive();
        assertThat(count("select count(*) from meal_portion where household_member_id = ?", childId)).isEqualTo(2);
        int childConsentBefore = count("select count(*) from consent_record where subject_member_id = ?", childId);
        int auditBefore = count("select count(*) from audit_event");

        mockMvc.perform(post("/api/v1/households/{h}/members/{m}/erase", householdId, childId)
                        .header("Authorization", guardian.authHeader()))
                .andExpect(status().isNoContent());

        // The child's record is gone.
        assertThat(count("select count(*) from health_observation where household_member_id = ?", childId)).isZero();
        assertThat(count("select count(*) from wellness_goal where household_member_id = ?", childId)).isZero();
        assertThat(count("select count(*) from habit where household_member_id = ?", childId)).isZero();
        assertThat(count("select count(*) from habit_completion where habit_id = ?", childHabit)).isZero();
        assertThat(count("select count(*) from meal_portion where household_member_id = ?", childId)).isZero();
        assertThat(count("select count(*) from meal where id = ?", snackId)).isZero();

        // Nothing of the guardian's is: the dinner survives with their share, their derived and
        // typed-in observations, their goal and their habit log are all still there.
        assertThat(count("select count(*) from meal where id = ?", dinnerId)).isEqualTo(1);
        assertThat(jdbc.queryForList("select household_member_id from meal_portion where meal_id = ?",
                UUID.class, dinnerId)).containsExactly(guardianMemberId);
        assertThat(count("select count(*) from health_observation where household_member_id = ?", guardianMemberId))
                .isEqualTo(guardianObservationsBefore);
        assertThat(count("select count(*) from health_observation where household_member_id = ? and source_ref = ?",
                guardianMemberId, dinnerId)).isEqualTo(guardianDerivedFromDinner);
        assertThat(count("select count(*) from wellness_goal where household_member_id = ?", guardianMemberId))
                .isEqualTo(1);
        assertThat(count("select count(*) from habit where household_member_id = ?", guardianMemberId)).isEqualTo(1);
        assertThat(count("select count(*) from habit_completion where habit_id = ?", guardianHabit)).isEqualTo(1);

        // Evidence survives (AC-9.3).
        assertThat(count("select count(*) from consent_record where subject_member_id = ?", childId))
                .isEqualTo(childConsentBefore);
        assertThat(count("select count(*) from audit_event")).isGreaterThanOrEqualTo(auditBefore);
    }

    // ========================================================================= fixture

    private TwoAdults twoTrackingAdults() throws Exception {
        Session erased = registerNewAccount();
        UUID householdId = createHousehold(erased, "Health Household", "Asha");
        UUID erasedMemberId = ownMemberId(erased, householdId);
        setProfile(erased, householdId, erasedMemberId, "A31_45");
        grantConsent(erased, erasedMemberId, "HEALTH_TRACKING");

        Session other = inviteAdult(erased, householdId, "Ravi");
        UUID otherMemberId = ownMemberId(other, householdId);
        setProfile(other, householdId, otherMemberId, "A31_45");
        grantConsent(other, otherMemberId, "HEALTH_TRACKING");

        recordObservation(erased, erasedMemberId, "WATER_ML", "500");
        recordObservation(erased, erasedMemberId, "WEIGHT_KG", "72.5");
        UUID otherObservationId = recordObservation(other, otherMemberId, "WATER_ML", "750");

        createGoal(erased, erasedMemberId, "WATER_ML", "AT_LEAST", "2000");
        UUID habitId = createHabit(erased, erasedMemberId, "Evening walk");
        LocalDate habitDay = LocalDate.now(HOUSEHOLD_ZONE).minusDays(1);
        completeHabit(erased, habitId, habitDay);

        JsonNode solo = logMeal(erased, householdId, "BREAKFAST", "Poha", "200", "180",
                Map.of(erasedMemberId, "1.0"));
        Map<UUID, String> familyPortions = new LinkedHashMap<>();
        familyPortions.put(erasedMemberId, "0.5");
        familyPortions.put(otherMemberId, "0.5");
        JsonNode family = logMeal(erased, householdId, "DINNER", "Dal", "400", "120", familyPortions);
        UUID familyMealId = UUID.fromString(family.get("id").asText());
        UUID otherPortionId = null;
        for (JsonNode portion : family.get("portions")) {
            if (otherMemberId.toString().equals(portion.get("householdMemberId").asText())) {
                otherPortionId = UUID.fromString(portion.get("id").asText());
            }
        }
        assertThat(otherPortionId).isNotNull();

        // The other adult adds their own share to their own log (D-041), so the family meal has
        // derived nutrition for someone other than the member about to be erased.
        mockMvc.perform(post("/api/v1/nutrition/meals/{id}/confirm", familyMealId)
                        .header("Authorization", other.authHeader()))
                .andExpect(status().isOk());

        UUID grantByErased = share(erased, erasedMemberId, otherMemberId, "ACTIVITY");
        UUID grantToErased = share(other, otherMemberId, erasedMemberId, "HYDRATION");

        return new TwoAdults(erased, householdId, erasedMemberId, other, otherMemberId, otherObservationId,
                habitId, habitDay, UUID.fromString(solo.get("id").asText()), familyMealId, otherPortionId,
                grantByErased, grantToErased);
    }

    // ========================================================================= helpers

    private Session registerNewAccount() throws Exception {
        String email = "health-rights-it-" + UUID.randomUUID() + "@example.com";
        JsonNode node = json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-sufficiently-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn());
        return new Session(UUID.fromString(node.get("accountId").asText()), node.get("accessToken").asText());
    }

    private UUID createHousehold(Session owner, String name, String ownerLabel) throws Exception {
        return id(mockMvc.perform(post("/api/v1/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","ownerDisplayLabel":"%s"}
                                """.formatted(name, ownerLabel)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private UUID ownMemberId(Session session, UUID householdId) throws Exception {
        JsonNode members = json(mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", session.authHeader()))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode m : members) {
            if (session.accountId().toString().equals(m.path("userAccountId").asText(null))) {
                return UUID.fromString(m.get("id").asText());
            }
        }
        throw new IllegalStateException("caller has no membership in " + householdId);
    }

    private Session inviteAdult(Session inviter, UUID householdId, String displayLabel) throws Exception {
        String token = json(mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", inviter.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"ADULT","email":"invitee-%s@example.com"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("token").asText();

        Session invitee = registerNewAccount();
        mockMvc.perform(post("/api/v1/households/invitations/accept")
                        .header("Authorization", invitee.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","displayLabel":"%s"}
                                """.formatted(token, displayLabel)))
                .andExpect(status().isOk());
        return invitee;
    }

    private UUID addChild(Session guardian, UUID householdId, String label) throws Exception {
        return id(mockMvc.perform(post("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", guardian.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberType":"CHILD","displayLabel":"%s","relationship":"PARENT","authorityBasis":"parent"}
                                """.formatted(label)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void setProfile(Session caller, UUID householdId, UUID memberId, String ageBand) throws Exception {
        mockMvc.perform(put("/api/v1/households/{h}/members/{m}/profile", householdId, memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ageBand":"%s","goals":[],"allergens":[],"conditions":[]}
                                """.formatted(ageBand)))
                .andExpect(status().isOk());
    }

    private void grantConsent(Session caller, UUID memberId, String purposeCode) throws Exception {
        mockMvc.perform(post("/api/v1/consent/members/{m}/grants", memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"purposeCode":"%s"}
                                """.formatted(purposeCode)))
                .andExpect(status().isCreated());
    }

    private UUID recordObservation(Session caller, UUID memberId, String metricCode, String value) throws Exception {
        return id(mockMvc.perform(post("/api/v1/wellness/members/{m}/observations", memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metricCode":"%s","value":%s}
                                """.formatted(metricCode, value)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private UUID createGoal(Session caller, UUID memberId, String metricCode, String direction, String target)
            throws Exception {
        return id(mockMvc.perform(post("/api/v1/goals")
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","metricCode":"%s","direction":"%s","targetValue":%s,"period":"DAILY"}
                                """.formatted(memberId, metricCode, direction, target)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private UUID createHabit(Session caller, UUID memberId, String name) throws Exception {
        return id(mockMvc.perform(post("/api/v1/habits")
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"memberId":"%s","name":"%s","cadence":"DAILY"}
                                """.formatted(memberId, name)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void completeHabit(Session caller, UUID habitId, LocalDate day) throws Exception {
        mockMvc.perform(put("/api/v1/habits/{id}/completions/{date}", habitId, day)
                        .header("Authorization", caller.authHeader()))
                .andExpect(status().isNoContent());
    }

    /** One custom food, with an energy figure per 100 g, apportioned as given (member → fraction). */
    private JsonNode logMeal(Session caller, UUID householdId, String mealType, String food,
                             String quantityG, String energyKcalPer100, Map<UUID, String> portions)
            throws Exception {
        String portionsJson = portions.entrySet().stream()
                .map(e -> """
                        {"householdMemberId":"%s","portionFraction":%s}""".formatted(e.getKey(), e.getValue()))
                .collect(Collectors.joining(","));
        return json(mockMvc.perform(post("/api/v1/nutrition/meals")
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"householdId":"%s","mealType":"%s","name":"%s",
                                 "items":[{"customName":"%s","quantityG":%s,"nutritionPer100":{"energyKcal":%s}}],
                                 "portions":[%s]}
                                """.formatted(householdId, mealType, food, food, quantityG, energyKcalPer100,
                                portionsJson)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private UUID share(Session subject, UUID subjectMemberId, UUID audienceMemberId, String scope) throws Exception {
        return id(mockMvc.perform(post("/api/v1/members/{m}/access-grants", subjectMemberId)
                        .header("Authorization", subject.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"audienceMemberId":"%s","scope":"%s","level":"VIEW"}
                                """.formatted(audienceMemberId, scope)))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID id(MvcResult result) throws Exception {
        return UUID.fromString(json(result).get("id").asText());
    }

    private int count(String sql, Object... args) {
        Integer n = jdbc.queryForObject(sql, Integer.class, args);
        return n == null ? 0 : n;
    }

    private static List<String> codesWithSource(JsonNode observations, String source) {
        List<String> codes = new ArrayList<>();
        observations.forEach(o -> {
            if (source.equals(o.get("source").asText())) {
                codes.add(o.get("metricCode").asText());
            }
        });
        return codes;
    }

    private static List<BigDecimal> valuesOf(JsonNode observations, String metricCode, String source) {
        List<BigDecimal> values = new ArrayList<>();
        observations.forEach(o -> {
            if (metricCode.equals(o.get("metricCode").asText()) && source.equals(o.get("source").asText())) {
                values.add(new BigDecimal(o.get("value").asText()));
            }
        });
        return values;
    }

    private static List<String> texts(JsonNode array) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.asText()));
        return out;
    }

    private static List<String> texts(JsonNode array, String field) {
        List<String> out = new ArrayList<>();
        array.forEach(n -> out.add(n.get(field).asText()));
        return out;
    }

    private static JsonNode findById(JsonNode array, UUID id) {
        for (JsonNode n : array) {
            if (id.toString().equals(n.get("id").asText())) {
                return n;
            }
        }
        throw new AssertionError("no element with id " + id);
    }
}
