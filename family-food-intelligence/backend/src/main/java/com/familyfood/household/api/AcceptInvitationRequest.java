package com.familyfood.household.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AcceptInvitationRequest(

        @NotBlank
        String token,

        @NotBlank
        @Size(max = 60)
        String displayLabel
) {}
