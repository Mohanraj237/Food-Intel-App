package com.familyfood.dashboard;

import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The family overview's week and household summary (D-043, D-053, D-054) end to end: real HTTP,
 * the real access policy, and a real database.
 *
 * <p>Two adults in one household. Asha shares ACTIVITY (VIEW) with Ravi and logs steps on two days
 * of the week and water today. What Ravi's overview must show: Asha's steps as a seven-day week
 * with gaps (written as {@code null}) on the days she logged nothing, and nothing at all of her
 * water; a summary that counts both of them for steps but only Ravi for water, because that is
 * all Ravi can see. Asha's own overview, where Ravi shares nothing, has no summary at all.
 */
class FamilyOverviewIT extends IntegrationTestBase {

    /** V14's default household zone (D-053). */
    private static final ZoneId HOUSEHOLD_ZONE = ZoneId.of("Asia/Kolkata");

    @Autowired
    private ObjectMapper objectMapper;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    @Test
    @DisplayName("a shared member's week has seven STEPS points with null gaps, no unshared water, and a summary of what the caller can see")
    void sharedActivityWeekAndSummary() throws Exception {
        Session asha = registerNewAccount();
        UUID householdId = createHousehold(asha, "Overview Household", "Asha");
        UUID ashaMember = ownMemberId(asha, householdId);
        setProfile(asha, householdId, ashaMember, "A31_45");
        grantConsent(asha, ashaMember, "HEALTH_TRACKING");

        Session ravi = inviteAdult(asha, householdId, "Ravi");
        UUID raviMember = ownMemberId(ravi, householdId);

        share(asha, ashaMember, raviMember, "ACTIVITY");

        // Explicit household-local instants, so the test cannot straddle midnight in India. Water
        // at today's local midnight is today and is never in the future.
        LocalDate today = LocalDate.now(HOUSEHOLD_ZONE);
        List<LocalDate> week = today.minusDays(6).datesUntil(today.plusDays(1)).toList();
        LocalDate stepsDayOne = today.minusDays(4);
        LocalDate stepsDayTwo = today.minusDays(1);
        recordObservation(asha, ashaMember, "STEPS", "4200", localTime(stepsDayOne, 9));
        recordObservation(asha, ashaMember, "STEPS", "6100", localTime(stepsDayTwo, 18));
        recordObservation(asha, ashaMember, "STEPS", "9999", localTime(today.minusDays(7), 9)); // before the week
        recordObservation(asha, ashaMember, "WATER_ML", "500", today.atStartOfDay(HOUSEHOLD_ZONE).toInstant());

        // ------------------------------------------------------------ Ravi's overview
        JsonNode overview = overview(ravi, householdId, today);

        assertThat(overview.get("timeZone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(overview.get("date").asText()).isEqualTo(today.toString());
        assertThat(texts(overview.get("week"))).containsExactlyElementsOf(week.stream().map(LocalDate::toString).toList());
        assertThat(overview.get("mealsAwaitingConfirmation").asInt()).isZero();

        JsonNode members = overview.get("members");
        assertThat(members).hasSize(2);
        JsonNode raviCard = members.get(0);
        JsonNode ashaCard = members.get(1);
        assertThat(raviCard.get("memberId").asText()).isEqualTo(raviMember.toString());
        assertThat(raviCard.get("isYou").asBoolean()).isTrue();
        assertThat(ashaCard.get("memberId").asText()).isEqualTo(ashaMember.toString());
        assertThat(ashaCard.get("isYou").asBoolean()).isFalse();
        assertThat(ashaCard.get("relationship").asText()).isEqualTo("SHARED");
        assertThat(texts(ashaCard.get("visibleScopes"))).containsExactly("ACTIVITY");
        assertThat(ashaCard.get("manageScopes")).isEmpty();

        // Asha's week: exactly one row — steps — and not a trace of her water.
        JsonNode ashaWeek = ashaCard.get("week");
        assertThat(texts(ashaWeek, "code")).containsExactly("STEPS");
        assertThat(texts(ashaWeek, "category")).doesNotContain("HYDRATION");
        JsonNode steps = ashaWeek.get(0);
        assertThat(steps.get("category").asText()).isEqualTo("ACTIVITY");
        assertThat(steps.get("aggregation").asText()).isEqualTo("SUM");
        Map<LocalDate, String> ashasSteps = Map.of(stepsDayOne, "4200", stepsDayTwo, "6100");
        assertDays(steps.get("days"), week, ashasSteps, "value", "count");
        // Nothing of hers today that Ravi may see (steps were not today; water is not shared).
        assertThat(ashaCard.get("today")).isEmpty();
        assertThat(ashaCard.get("goals")).isEmpty();
        assertThat(ashaCard.get("habits").isNull()).isTrue();
        assertThat(ashaCard.get("habitsWeek").isNull()).isTrue();
        assertThat(ashaCard.get("trackingConsentActive").isNull()).isTrue();
        // Her 500 ml is on no card Ravi receives, in any form.
        for (JsonNode card : members) {
            assertThat(texts(card.get("week"), "code")).doesNotContain("WATER_ML");
            assertThat(texts(card.get("today"), "code")).doesNotContain("WATER_ML");
        }

        // Ravi's own card: nothing logged, but his habits week is there (as zeros).
        assertThat(raviCard.get("week")).isEmpty();
        assertThat(raviCard.get("habitsWeek")).hasSize(7);

        // --------------------------------------------------------------- the summary
        JsonNode summary = overview.get("summary");
        assertThat(summary.isNull()).as("summary with Ravi and Asha's activity included").isFalse();
        assertThat(texts(summary.get("includedMemberIds")))
                .containsExactly(raviMember.toString(), ashaMember.toString());
        JsonNode metrics = summary.get("metrics");
        assertThat(texts(metrics, "code")).containsExactly("WATER_ML", "STEPS", "SLEEP_MINUTES");

        // Steps: both people are eligible; only Asha logged, so each total is hers alone.
        JsonNode familySteps = byCode(metrics, "STEPS");
        assertThat(familySteps.get("eligiblePeople").asInt()).isEqualTo(2);
        assertDays(familySteps.get("days"), week, ashasSteps, "total", "contributors");

        // Water: only Ravi's own is visible to Ravi, and he logged none — eligible 1, no totals.
        JsonNode familyWater = byCode(metrics, "WATER_ML");
        assertThat(familyWater.get("eligiblePeople").asInt()).isEqualTo(1);
        assertDays(familyWater.get("days"), week, Map.of(), "total", "contributors");
        assertThat(byCode(metrics, "SLEEP_MINUTES").get("eligiblePeople").asInt()).isEqualTo(1);
        assertThat(summary.get("meals").get("eligiblePeople").asInt()).isEqualTo(1);
        assertThat(summary.get("habits")).hasSize(7);

        // --------------------------------------------------- Asha's overview, for contrast
        JsonNode ashaView = overview(asha, householdId, today);
        assertThat(ashaView.get("summary").isNull()).as("Ravi shares nothing with Asha").isTrue();
        JsonNode ravisCardForAsha = ashaView.get("members").get(1);
        assertThat(ravisCardForAsha.get("relationship").asText()).isEqualTo("NONE");
        assertThat(ravisCardForAsha.get("visibleScopes")).isEmpty();
        assertThat(ravisCardForAsha.get("week")).isEmpty();
        JsonNode ashasOwnWeek = ashaView.get("members").get(0).get("week");
        assertThat(texts(ashasOwnWeek, "code")).containsExactly("STEPS", "WATER_ML");
        assertThat(ashaView.get("mealsAwaitingConfirmation").asInt()).isZero();
    }

    // ========================================================================= helpers

    /**
     * One JSON entry per day of {@code week}, in order. A day in {@code logged} carries that
     * number with a count of 1; every other day is a gap — the number field present and
     * {@code null} (never a zero, never a missing key) with a count of 0.
     */
    private static void assertDays(JsonNode days, List<LocalDate> week, Map<LocalDate, String> logged,
                                   String numberField, String countField) {
        assertThat(days).hasSize(week.size());
        for (int i = 0; i < week.size(); i++) {
            JsonNode day = days.get(i);
            LocalDate date = week.get(i);
            assertThat(day.get("date").asText()).isEqualTo(date.toString());
            assertThat(day.has(numberField)).as("%s key on %s", numberField, date).isTrue();
            String expected = logged.get(date);
            if (expected == null) {
                assertThat(day.get(numberField).isNull()).as("%s on %s", numberField, date).isTrue();
                assertThat(day.get(countField).asInt()).as("%s on %s", countField, date).isZero();
            } else {
                assertThat(day.get(numberField).decimalValue()).as("%s on %s", numberField, date)
                        .isEqualByComparingTo(expected);
                assertThat(day.get(countField).asInt()).as("%s on %s", countField, date).isEqualTo(1);
            }
        }
    }

    private JsonNode overview(Session caller, UUID householdId, LocalDate date) throws Exception {
        return json(mockMvc.perform(get("/api/v1/households/{id}/overview", householdId)
                        .param("date", date.toString())
                        .header("Authorization", caller.authHeader()))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static Instant localTime(LocalDate day, int hour) {
        return day.atTime(hour, 0).atZone(HOUSEHOLD_ZONE).toInstant();
    }

    private Session registerNewAccount() throws Exception {
        String email = "family-overview-it-" + UUID.randomUUID() + "@example.com";
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

    private void recordObservation(Session caller, UUID memberId, String metricCode, String value,
                                   Instant effectiveAt) throws Exception {
        mockMvc.perform(post("/api/v1/wellness/members/{m}/observations", memberId)
                        .header("Authorization", caller.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"metricCode":"%s","value":%s,"effectiveAt":"%s"}
                                """.formatted(metricCode, value, effectiveAt)))
                .andExpect(status().isCreated());
    }

    private void share(Session subject, UUID subjectMemberId, UUID audienceMemberId, String scope) throws Exception {
        mockMvc.perform(post("/api/v1/members/{m}/access-grants", subjectMemberId)
                        .header("Authorization", subject.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"audienceMemberId":"%s","scope":"%s","level":"VIEW"}
                                """.formatted(audienceMemberId, scope)))
                .andExpect(status().isCreated());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private UUID id(MvcResult result) throws Exception {
        return UUID.fromString(json(result).get("id").asText());
    }

    private static JsonNode byCode(JsonNode array, String code) {
        for (JsonNode n : array) {
            if (code.equals(n.get("code").asText())) {
                return n;
            }
        }
        throw new AssertionError("no element with code " + code);
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
}
