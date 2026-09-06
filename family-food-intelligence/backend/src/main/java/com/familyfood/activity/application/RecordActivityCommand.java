package com.familyfood.activity.application;

import com.familyfood.activity.domain.ActivityEventType;

import java.util.UUID;

public record RecordActivityCommand(UUID productId, UUID alternativeProductId, ActivityEventType eventType) {
}
