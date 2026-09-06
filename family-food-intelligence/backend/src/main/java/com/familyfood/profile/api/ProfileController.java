package com.familyfood.profile.api;

import com.familyfood.platform.security.CurrentUser;
import com.familyfood.profile.application.ProfileService;
import com.familyfood.profile.application.ProfileView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read and replace a household member's profile.
 *
 * <p>Thin by design (Constitution §36): bind, extract the caller's own identity from
 * {@link CurrentUser} — never from the path — delegate to {@link ProfileService}, map the
 * result. All authorization (D-001, D-002) happens inside the service.
 *
 * <p><b>{@code householdId} in the path is not an authorization input.</b> The service
 * decides access solely from {@code memberId} plus the caller's own account id, resolved
 * through the published {@code HouseholdAccess} interface — the same rule the task
 * contract specifies. Accepting {@code householdId} here is for a readable, RESTful URL
 * and for future routing only; a caller cannot widen or narrow their access by putting a
 * different household id in the path than the one {@code memberId} actually belongs to.
 * A mismatch is harmless, not exploitable: {@code HouseholdAccess.findMember(memberId)}
 * returns the member's real household regardless of what the URL claims.
 */
@RestController
@RequestMapping("/api/v1/households/{householdId}/members/{memberId}/profile")
@Tag(name = "Profile")
public class ProfileController {

    private final ProfileService profileService;
    private final CurrentUser currentUser;

    public ProfileController(ProfileService profileService, CurrentUser currentUser) {
        this.profileService = profileService;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Operation(summary = "Get the current profile version for a household member",
               description = "Returns the single is_current version. An adult may read only "
                           + "their own profile; a child's profile may be read only by one "
                           + "of their guardians. Consent-gating of this read is a separate, "
                           + "later integration.")
    public ProfileResponse get(@PathVariable UUID householdId,
                               @PathVariable("memberId") UUID memberId) {
        ProfileView view = profileService.getCurrentProfile(
                currentUser.require().accountId(), memberId);
        return ProfileResponse.from(view);
    }

    @PutMapping
    @Operation(summary = "Create or replace the current profile version for a household member",
               description = "Writes a brand-new version: the previous current version, if "
                           + "any, is superseded and the new one becomes current. Nothing is "
                           + "ever overwritten in place.")
    public ProfileResponse put(@PathVariable UUID householdId,
                               @Parameter(description = "The household member the profile belongs to")
                               @PathVariable("memberId") UUID memberId,
                               @Valid @RequestBody ProfileRequest request) {
        ProfileView view = profileService.createOrUpdateProfile(
                currentUser.require().accountId(),
                memberId,
                request.ageBand(),
                request.sex(),
                request.dietaryPreference(),
                request.goals(),
                request.allergens(),
                request.conditions());
        return ProfileResponse.from(view);
    }
}
