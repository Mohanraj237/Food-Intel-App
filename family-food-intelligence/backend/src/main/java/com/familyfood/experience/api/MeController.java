package com.familyfood.experience.api;

import com.familyfood.experience.application.ExperienceService.MemberExperience;
import com.familyfood.experience.application.MeService;
import com.familyfood.experience.application.MeService.Provisioned;
import com.familyfood.experience.application.MeView;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * "Me" and experience tiers (D-043, D-046).
 *
 * <p>Thin by design: the caller comes from {@link CurrentUser}, never from the request, and every
 * rule lives in {@link MeService} and the services it composes. A member id in a path names the
 * member being asked about, never the caller.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Me")
public class MeController {

    private final MeService me;
    private final CurrentUser currentUser;

    public MeController(MeService me, CurrentUser currentUser) {
        this.me = me;
        this.currentUser = currentUser;
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-in person: lens, household, experience tier and capabilities",
               description = "mode is NONE until the person has a household, INDIVIDUAL for a "
                           + "household of one made through \"Just me\", FAMILY otherwise. The "
                           + "primary household is the oldest live membership. Capabilities are "
                           + "stated by the server; a client renders them and never infers them.")
    public MeView me() {
        return me.me(currentUser.require().accountId());
    }

    @PostMapping("/me/individual")
    @Operation(summary = "\"Just me\": create a household of one (idempotent)",
               description = "201 with the new view when a household was created; 200 with the "
                           + "existing view when the caller already belongs to a household — a "
                           + "second household is never made.")
    public ResponseEntity<MeView> provisionIndividual(@Valid @RequestBody ProvisionIndividualRequest request) {
        Provisioned result = me.provisionIndividual(currentUser.require().accountId(), request.displayLabel());
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result.me());
    }

    @GetMapping("/members/{memberId}/experience")
    @Operation(summary = "A household member's experience tier and capabilities",
               description = "The full tier for the member, their guardian or a caregiver they "
                           + "chose; otherwise only CHILD or ADULT, which the member list already "
                           + "shows. Not found for anyone outside the member's household.")
    public MemberExperience experience(@PathVariable UUID memberId) {
        return me.experienceOf(currentUser.require().accountId(), memberId);
    }

    /**
     * @param displayLabel what the person calls themselves; also the household's name. 1 to 60
     *                     characters after trimming — the length is checked by the service, after
     *                     the trim, so surrounding spaces never count against it.
     */
    public record ProvisionIndividualRequest(@NotBlank String displayLabel) {}
}
