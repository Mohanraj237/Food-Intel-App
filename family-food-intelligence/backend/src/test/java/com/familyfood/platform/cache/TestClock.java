package com.familyfood.platform.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** A clock the test moves by hand, for the breaker's retry-after window. */
final class TestClock extends Clock {

    private volatile Instant now;

    TestClock(Instant start) {
        this.now = start;
    }

    void advance(Duration by) {
        now = now.plus(by);
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }
}
