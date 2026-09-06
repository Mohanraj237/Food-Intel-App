package com.familyfood.recommendation.application;

import com.familyfood.catalog.application.IngestProductCommand;
import com.familyfood.catalog.application.ProductIngestionService;
import com.familyfood.catalog.domain.AllergenDeclaration;
import com.familyfood.catalog.domain.CaptureMethod;
import com.familyfood.catalog.domain.DataQuality;
import com.familyfood.catalog.domain.SourceType;
import com.familyfood.catalog.domain.VersionStatus;
import com.familyfood.consent.application.ConsentService;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.domain.AgeBand;
import com.familyfood.profile.domain.AllergenCode;
import com.familyfood.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link AlternativesService} against a real database, real household/profile/consent
 * modules, and real catalogue products (via {@link ProductIngestionService} — the same
 * write path ops-review approval and the Open Food Facts importer use).
 *
 * <p>Covers exactly the questions Phase 6 hardening asks of a new authorization-adjacent
 * endpoint: cross-household denial, an uncategorised product (the exact edge case a Phase 6
 * review found bypassed the household-membership check entirely — see the reordering fix in
 * {@link AlternativesServiceImpl#findAlternatives}), real allergen-safety differentiation,
 * and D-033's own commitment never to invent a nutrient-direction judgment.
 */
class AlternativesServiceIT extends IntegrationTestBase {

    // Each test that inspects alternatives content uses its own category: candidate
    // products persist for the life of the shared test container (no per-test rollback in
    // this suite, matching every other *IT class here), so sharing a category across tests
    // would let one test's leftover products pollute another's candidate pool.
    private static final String CATEGORY = "BISCUITS";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AlternativesService alternativesService;

    @Autowired
    private ProductIngestionService productIngestionService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private ConsentService consentService;

    private record Session(UUID accountId, String accessToken) {
        String authHeader() {
            return "Bearer " + accessToken;
        }
    }

    private Session registerNewAccount() throws Exception {
        String email = "alt-it-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"a-sufficiently-long-passphrase"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Session(UUID.fromString(node.get("accountId").asText()), node.get("accessToken").asText());
    }

    private UUID createHousehold(Session owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/households")
                        .header("Authorization", owner.authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Alternatives Test Household","ownerDisplayLabel":"Me"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText());
    }

    private UUID ownerMemberId(Session owner, UUID householdId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/households/{id}/members", householdId)
                        .header("Authorization", owner.authHeader()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode members = objectMapper.readTree(result.getResponse().getContentAsString());
        for (JsonNode m : members) {
            if (m.get("role").asText().equals("OWNER")) {
                return UUID.fromString(m.get("id").asText());
            }
        }
        throw new IllegalStateException("no OWNER member found for household " + householdId);
    }

    private UUID ingestProduct(String name, String categoryCode, List<AllergenCode> allergenCodes) {
        List<IngestProductCommand.AllergenValue> allergens = allergenCodes.stream()
                .map(a -> new IngestProductCommand.AllergenValue(a.name(), AllergenDeclaration.CONTAINS))
                .toList();
        IngestProductCommand cmd = new IngestProductCommand(
                name, "TestBrand", categoryCode, null, null, null, null, null, null,
                null, List.of(), List.of(), allergens,
                SourceType.MANUAL_ENTRY, CaptureMethod.MANUAL_ENTRY, null, null, null,
                VersionStatus.NEEDS_VERIFICATION, DataQuality.UNVERIFIED, null, null);
        return productIngestionService.ingestNewProduct(cmd);
    }

    // ------------------------------------------------------------------------- authorization

    @Test
    @DisplayName("unauthenticated request is rejected before any household or product data is touched")
    void unauthenticatedIsRejected() throws Exception {
        UUID productId = ingestProduct("Unauth Test Biscuit", CATEGORY, List.of());
        mockMvc.perform(get("/api/v1/households/{h}/products/{p}/alternatives",
                        UUID.randomUUID(), productId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a caller who is not a member of the target household is denied, even though it is real and populated")
    void crossHouseholdIsDenied() throws Exception {
        Session ownerA = registerNewAccount();
        UUID householdA = createHousehold(ownerA);

        Session ownerB = registerNewAccount();
        UUID householdB = createHousehold(ownerB);

        UUID productId = ingestProduct("Cross Household Biscuit", CATEGORY, List.of());

        assertThatThrownBy(() -> alternativesService.findAlternatives(ownerA.accountId(), householdB, productId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.NOT_HOUSEHOLD_MEMBER);
    }

    @Test
    @DisplayName("an unknown product id is a real not-found, not an empty result")
    void unknownProductIsNotFound() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);

        assertThatThrownBy(() -> alternativesService.findAlternatives(owner.accountId(), householdId, UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    /**
     * The exact bypass a Phase 6 hardening review found and fixed: an uncategorised product
     * short-circuited to an empty result before the household-membership check ever ran. A
     * caller with no real membership in {@code householdB} must still be denied, not handed
     * a trivially-empty 200.
     */
    @Test
    @DisplayName("an uncategorised product still enforces household membership before returning its honest empty result")
    void uncategorisedProductStillEnforcesHouseholdMembership() throws Exception {
        Session ownerA = registerNewAccount();
        Session ownerB = registerNewAccount();
        UUID householdB = createHousehold(ownerB);

        UUID uncategorisedProductId = ingestProduct("No Category Product", null, List.of());

        assertThatThrownBy(() ->
                alternativesService.findAlternatives(ownerA.accountId(), householdB, uncategorisedProductId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).code())
                .isEqualTo(ErrorCode.NOT_HOUSEHOLD_MEMBER);

        // A real member, by contrast, gets the honest empty result — not an error.
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        AlternativesResult result =
                alternativesService.findAlternatives(owner.accountId(), householdId, uncategorisedProductId);
        assertThat(result.alternatives()).isEmpty();
    }

    @Test
    @DisplayName("no same-category candidates -> an honest empty result, not an error")
    void noCandidatesIsAnHonestEmptyResult() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID productId = ingestProduct("Lonely Category Product", "EDIBLE_OIL", List.of());

        AlternativesResult result = alternativesService.findAlternatives(owner.accountId(), householdId, productId);

        assertThat(result.productId()).isEqualTo(productId);
        assertThat(result.householdId()).isEqualTo(householdId);
        assertThat(result.alternatives()).isEmpty();
    }

    // --------------------------------------------------------------- allergen-safety ranking

    @Test
    @DisplayName("a same-category candidate free of the caller's allergen is surfaced as safer; one sharing it is not shown at all")
    void saferAllergenCandidateIsSurfacedEquallyUnsafeOneIsNot() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID memberId = ownerMemberId(owner, householdId);

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(), Set.of(AllergenCode.PEANUT), Set.of());

        UUID currentProductId = ingestProduct("Peanut Namkeen", "NAMKEEN", List.of(AllergenCode.PEANUT));
        UUID saferCandidateId = ingestProduct("Peanut-Free Namkeen", "NAMKEEN", List.of());
        UUID equallyUnsafeCandidateId = ingestProduct("Also Peanut Namkeen", "NAMKEEN", List.of(AllergenCode.PEANUT));

        AlternativesResult result =
                alternativesService.findAlternatives(owner.accountId(), householdId, currentProductId);

        assertThat(result.alternatives()).extracting(AlternativeView::productId)
                .contains(saferCandidateId)
                .doesNotContain(equallyUnsafeCandidateId, currentProductId);

        AlternativeView safer = result.alternatives().stream()
                .filter(a -> a.productId().equals(saferCandidateId)).findFirst().orElseThrow();
        assertThat(safer.betterOn()).extracting(AlternativeReason::code).contains("SAFER_ALLERGENS");
        assertThat(safer.betterOn()).extracting(AlternativeReason::memberId).contains(memberId);
        assertThat(safer.betterOn()).extracting(AlternativeReason::allergenCode).contains("PEANUT");
        // D-013: both sides always present, even though nothing is worse here.
        assertThat(safer.worseOn()).isEmpty();
    }

    @Test
    @DisplayName("a candidate that would introduce a new allergen risk for the caller is never returned, even as a 'worse' option")
    void newAllergenRiskCandidateIsExcludedEntirely() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID memberId = ownerMemberId(owner, householdId);

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(), Set.of(AllergenCode.MILK), Set.of());

        UUID currentProductId = ingestProduct("Milk-Free Beverage", "BEVERAGES", List.of());
        UUID riskyCandidateId = ingestProduct("Contains Milk Beverage", "BEVERAGES", List.of(AllergenCode.MILK));

        AlternativesResult result =
                alternativesService.findAlternatives(owner.accountId(), householdId, currentProductId);

        assertThat(result.alternatives()).extracting(AlternativeView::productId)
                .doesNotContain(riskyCandidateId);
    }

    @Test
    @DisplayName("no invented nutrient-direction judgment (D-033): a candidate differing only in raw nutrient "
            + "values, with no allergen or verdict differentiator, is not returned at all")
    void noFabricatedNutrientJudgment() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID currentProductId = ingestProduct("Plain Noodles A", "INSTANT_NOODLES", List.of());
        // Differs only in name/brand identity — no allergen or (today, inert) verdict signal
        // exists to differentiate it, so D-033 says: don't show it, don't guess.
        ingestProduct("Plain Noodles B", "INSTANT_NOODLES", List.of());

        AlternativesResult result =
                alternativesService.findAlternatives(owner.accountId(), householdId, currentProductId);

        assertThat(result.alternatives()).isEmpty();
    }

    @Test
    @DisplayName("evaluate is deterministic: identical inputs produce the same alternatives in the same order")
    void deterministicOrdering() throws Exception {
        Session owner = registerNewAccount();
        UUID householdId = createHousehold(owner);
        UUID memberId = ownerMemberId(owner, householdId);

        consentService.grant(owner.accountId(), memberId, "PERSONAL_FIT", Map.of());
        profileService.createOrUpdateProfile(owner.accountId(), memberId, AgeBand.A18_30, null, null,
                Set.of(), Set.of(AllergenCode.SOY), Set.of());

        UUID currentProductId = ingestProduct("Soy Atta", "STAPLES", List.of(AllergenCode.SOY));
        ingestProduct("Soy-Free Atta One", "STAPLES", List.of());
        ingestProduct("Soy-Free Atta Two", "STAPLES", List.of());

        AlternativesResult first = alternativesService.findAlternatives(owner.accountId(), householdId, currentProductId);
        AlternativesResult second = alternativesService.findAlternatives(owner.accountId(), householdId, currentProductId);

        assertThat(first.alternatives()).extracting(AlternativeView::productId)
                .containsExactlyElementsOf(second.alternatives().stream().map(AlternativeView::productId).toList());
    }
}
