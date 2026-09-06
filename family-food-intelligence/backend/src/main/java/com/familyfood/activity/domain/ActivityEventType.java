package com.familyfood.activity.domain;

/** Mirrors the {@code activity_event.event_type} CHECK constraint in V10__activity.sql. */
public enum ActivityEventType {
    VIEWED, SCANNED, COMPARED, BOUGHT, BOUGHT_ALTERNATIVE, BOUGHT_NEITHER, SKIPPED
}
