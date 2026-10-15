package com.familyfood.dashboard.api;

import com.familyfood.dashboard.application.FamilyOverviewService;
import com.familyfood.dashboard.application.FamilyOverviewView;
import com.familyfood.dashboard.application.TodayService;
import com.familyfood.dashboard.application.TodayView;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * The Today summary and the family overview (D-043, D-044, D-053).
 *
 * <p>Read-only and thin: the caller comes from {@link CurrentUser}; every access decision is the
 * one policy's, made inside the services. Path ids name what is being looked at, never who is
 * looking.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Dashboard")
public class DashboardController {

    private final TodayService today;
    private final FamilyOverviewService overview;
    private final CurrentUser currentUser;

    public DashboardController(TodayService today, FamilyOverviewService overview, CurrentUser currentUser) {
        this.today = today;
        this.overview = overview;
        this.currentUser = currentUser;
    }

    @GetMapping("/members/{memberId}/today")
    @Operation(summary = "One member's day: what was logged, goals, habits, meals to confirm",
               description = "Restricted to the scopes the caller may see; 403 when they may see "
                           + "nothing. Metrics are summed or take the latest reading as the metric "
                           + "defines, and only metrics with data are listed — never a zero for "
                           + "something not logged. The day is the household's (default today "
                           + "there); a future date is a 400.")
    public TodayView today(@PathVariable UUID memberId,
                           @Parameter(description = "Household-local date, YYYY-MM-DD; default today")
                           @RequestParam(required = false) String date) {
        return today.today(currentUser.require().accountId(), memberId, parseDate(date));
    }

    @GetMapping("/households/{householdId}/overview")
    @Operation(summary = "Every member of a household, each as far as the caller may see them",
               description = "Members who share nothing with the caller appear as an identity card "
                           + "only. No totals across members and no ranking. Order: you, then the "
                           + "children you guard, then everyone else by when they joined.")
    public FamilyOverviewView overview(@PathVariable UUID householdId,
                                       @Parameter(description = "Household-local date, YYYY-MM-DD; default today")
                                       @RequestParam(required = false) String date) {
        return overview.overview(currentUser.require().accountId(), householdId, parseDate(date));
    }

    /**
     * Parsed here rather than bound as a {@code LocalDate}, so a malformed date is a 400 naming
     * the field — a failed conversion would otherwise surface as the generic "not found".
     */
    private static LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException e) {
            throw AppException.validation("date", "must be an ISO date, YYYY-MM-DD");
        }
    }
}
