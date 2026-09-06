package com.familyfood.activity.api;

import com.familyfood.activity.domain.ActivityEventType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RecordActivityRequest(
        @NotNull UUID productId,
        UUID alternativeProductId,
        @NotNull ActivityEventType eventType) {
}
