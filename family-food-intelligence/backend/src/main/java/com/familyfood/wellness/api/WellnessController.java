package com.familyfood.wellness.api;

import com.familyfood.platform.security.CurrentUser;
import com.familyfood.wellness.application.ObservationView;
import com.familyfood.wellness.application.RecordObservationCommand;
import com.familyfood.wellness.application.WellnessService;
import com.familyfood.wellness.domain.MetricCategory;
import com.familyfood.wellness.domain.MetricCode;
import com.familyfood.wellness.domain.ObservationSource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Health observations and their visibility.
 *
 * <p>Requires sign-in; not in {@code SecurityConfig}'s permit-all list. Every route is scoped to
 * a named subject member and authorized inside {@link WellnessService}, which consults the one
 * access policy (D-044), the age gate (D-036) and the consent gate (D-048) — deliberately not
 * here, so a second entry point cannot bypass them.
 *
 * <p>Sharing moved to {@code /api/v1/members/{id}/access-grants} in W1 (D-044), where it covers
 * every health module rather than observations alone. {@code /visibility} keeps its shape.
 *
 * <p>There is no "interpret this value" route, and there will not be one until a citable
 * reviewed standard exists per metric family (D-037).
 */
@RestController
@RequestMapping("/api/v1/wellness")
@Tag(name = "Wellness")
public class WellnessController {

    /** A default window wide enough for a trend, narrow enough to stay a cheap query. */
    private static final int DEFAULT_WINDOW_DAYS = 90;

    private final WellnessService wellness;
    private final CurrentUser currentUser;
    private final Clock clock;

    public WellnessController(WellnessService wellness, CurrentUser currentUser, Clock clock) {
        this.wellness = wellness;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    /**
     * The metric taxonomy, so a client never hardcodes codes, units or — most importantly —
     * which metrics are permitted for a child. A UI that hardcoded that list would drift out
     * of step with the domain the moment a metric changed class.
     */
    @GetMapping("/metrics")
    @Operation(summary = "Every metric this product stores, with its unit and age eligibility")
    public List<MetricDefinitionResponse> metrics() {
        return Arrays.stream(MetricCode.values())
                .map(MetricDefinitionResponse::from)
                .toList();
    }

    /**
     * Records a reading a person typed in. The source is always {@code MANUAL}: a client
     * cannot label its own write DERIVED (that is what a meal produces) or IMPORT (reserved for
     * a device integration), because the label is how a history says where a number came from.
     */
    @PostMapping("/members/{memberId}/observations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record one measurement for a household member",
               description = "The unit is fixed by the metric; it is not accepted from the "
                           + "caller. A body or clinical metric on a child subject is refused "
                           + "with METRIC_NOT_PERMITTED_FOR_AGE (D-036) — this is a product "
                           + "commitment, not an authorization failure. Needs the subject's "
                           + "tracking consent (D-048).")
    public ObservationView record(@PathVariable UUID memberId,
                                  @Valid @RequestBody RecordObservationRequest request) {
        UUID accountId = currentUser.require().accountId();
        return wellness.record(accountId, new RecordObservationCommand(
                memberId, request.metricCode(), request.value(),
                request.effectiveAt(), ObservationSource.MANUAL));
    }

    @GetMapping("/members/{memberId}/observations")
    @Operation(summary = "Observations for one member in a window, restricted to categories "
                       + "the caller may see")
    public List<ObservationView> window(@PathVariable UUID memberId,
                                        @RequestParam(required = false) Instant from,
                                        @RequestParam(required = false) Instant to) {
        UUID accountId = currentUser.require().accountId();
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS) : from;
        return wellness.window(accountId, memberId, start, end);
    }

    @GetMapping("/members/{memberId}/observations/{metricCode}")
    @Operation(summary = "One metric's history for one member")
    public List<ObservationView> history(@PathVariable UUID memberId,
                                         @PathVariable MetricCode metricCode,
                                         @RequestParam(required = false) Instant from,
                                         @RequestParam(required = false) Instant to) {
        UUID accountId = currentUser.require().accountId();
        Instant end = to == null ? clock.instant() : to;
        Instant start = from == null ? end.minus(DEFAULT_WINDOW_DAYS, ChronoUnit.DAYS) : from;
        return wellness.history(accountId, memberId, metricCode, start, end);
    }

    /**
     * Which categories the caller may see about this member.
     *
     * <p>Lets a client show "Aarav shares activity with you" rather than discovering the
     * boundary by getting a 403, which is the difference between a designed empty state and
     * an error screen.
     */
    @GetMapping("/members/{memberId}/visibility")
    @Operation(summary = "Categories of this member's data the caller may see")
    public Set<MetricCategory> visibleCategories(@PathVariable UUID memberId) {
        return wellness.visibleCategories(currentUser.require().accountId(), memberId);
    }
}
