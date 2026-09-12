package com.familyfood.goals.api;

import com.familyfood.goals.application.CreateGoalCommand;
import com.familyfood.goals.application.GoalProgressView;
import com.familyfood.goals.application.GoalService;
import com.familyfood.goals.application.GoalView;
import com.familyfood.platform.security.CurrentUser;
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

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Nutrition and wellness targets (D-034 Phase 2).
 *
 * <p>Requires sign-in; not in {@code SecurityConfig}'s permit-all list. Authorization, the
 * D-036 age gate and the one-live-goal-per-metric rule are all enforced in
 * {@link GoalService} and deliberately not here, so that a second entry point added later
 * cannot bypass them.
 */
@RestController
@RequestMapping("/api/v1/goals")
@Tag(name = "Goals")
public class GoalController {

    private final GoalService goals;
    private final CurrentUser currentUser;

    public GoalController(GoalService goals, CurrentUser currentUser) {
        this.goals = goals;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a goal for a household member",
               description = "A body or clinical metric on a child subject is refused with "
                           + "METRIC_NOT_PERMITTED_FOR_AGE by the same check that refuses the "
                           + "matching observation (D-036). A second live goal for the same "
                           + "member and metric is refused with GOAL_ALREADY_ACTIVE — end the "
                           + "first one, so progress stays attributable to the target that was "
                           + "actually in force.")
    public GoalView create(@Valid @RequestBody CreateGoalRequest request) {
        UUID accountId = currentUser.require().accountId();
        return goals.createGoal(accountId, new CreateGoalCommand(
                request.memberId(), request.metricCode(), request.direction(),
                request.targetValue(), request.period()));
    }

    @GetMapping
    @Operation(summary = "Goals for one member, newest first")
    public List<GoalView> list(@RequestParam UUID memberId,
                               @RequestParam(defaultValue = "false") boolean includeEnded) {
        return goals.listGoals(currentUser.require().accountId(), memberId, includeEnded);
    }

    /**
     * POST rather than DELETE, because the goal is not deleted. It stops applying and the row
     * stays, so that progress a member was shown last month remains attributable to the target
     * that was in force then.
     */
    @PostMapping("/{goalId}/end")
    @Operation(summary = "Stop a goal. The record survives with an end date; nothing is deleted.")
    public GoalView end(@PathVariable UUID goalId) {
        return goals.endGoal(currentUser.require().accountId(), goalId);
    }

    /**
     * Actual against target for every live goal.
     *
     * <p>Omit {@code from} and {@code to} for the reading a member expects: each goal uses its
     * own period, so a daily goal reports today and a weekly goal the last seven days. Each
     * result echoes the window it used.
     */
    @GetMapping("/progress")
    @Operation(summary = "Measured amount against target for each live goal",
               description = "Where nothing was logged in the window the result says so — "
                           + "dataAvailable is false and actual is absent. It is never "
                           + "reported as zero, because an unlogged day is not a measured zero.")
    public List<GoalProgressView> progress(@RequestParam UUID memberId,
                                           @RequestParam(required = false) LocalDate from,
                                           @RequestParam(required = false) LocalDate to) {
        return goals.goalProgress(currentUser.require().accountId(), memberId, from, to);
    }
}
