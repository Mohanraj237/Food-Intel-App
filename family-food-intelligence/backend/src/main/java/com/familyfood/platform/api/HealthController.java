package com.familyfood.platform.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

/**
 * A minimal liveness probe for the client.
 *
 * Separate from {@code /actuator/health}, which is an operational endpoint whose
 * detail can disclose infrastructure topology and is therefore locked down. This
 * one is deliberately uninformative: it confirms the API is reachable and nothing
 * more, so a client can tell "service unreachable" from "product not found"
 * (Constitution §46) without learning anything about the deployment.
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Platform")
public class HealthController {

    private final Clock clock;

    public HealthController(Clock clock) {
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Confirm the API is reachable")
    public HealthResponse health() {
        return new HealthResponse("ok", clock.instant());
    }

    public record HealthResponse(String status, Instant time) {}
}
