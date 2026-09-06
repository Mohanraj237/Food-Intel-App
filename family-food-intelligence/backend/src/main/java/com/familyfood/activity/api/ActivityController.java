package com.familyfood.activity.api;

import com.familyfood.activity.application.ActivityService;
import com.familyfood.activity.application.RecordActivityCommand;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Outcome capture and recent activity (FR-012, D-005, D-027's documented interim state).
 * Requires sign-in; not in {@code SecurityConfig}'s permit-all list, same as every other
 * self-only route. Every event and every "recent" listing is scoped to the caller's own
 * account only — never another account's (D-002's self-only pattern, applied here).
 */
@RestController
@RequestMapping("/api/v1/activity")
@Tag(name = "Activity")
public class ActivityController {

    private final ActivityService activityService;
    private final CurrentUser currentUser;

    public ActivityController(ActivityService activityService, CurrentUser currentUser) {
        this.activityService = activityService;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Record one activity event for the caller's own account",
               description = "VIEWED | SCANNED | COMPARED | BOUGHT | BOUGHT_ALTERNATIVE | "
                           + "BOUGHT_NEITHER | SKIPPED. The product (and alternativeProductId, "
                           + "if given) must resolve in the catalogue.")
    public RecordActivityResponse record(@Valid @RequestBody RecordActivityRequest request) {
        UUID accountId = currentUser.require().accountId();
        UUID id = activityService.record(accountId,
                new RecordActivityCommand(request.productId(), request.alternativeProductId(), request.eventType()));
        return new RecordActivityResponse(id);
    }

    @GetMapping("/recent")
    @Operation(summary = "The caller's own most recent activity, newest first")
    public List<RecentActivityResponse> recent() {
        UUID accountId = currentUser.require().accountId();
        return activityService.recentForAccount(accountId).stream()
                .map(RecentActivityResponse::from)
                .toList();
    }
}
