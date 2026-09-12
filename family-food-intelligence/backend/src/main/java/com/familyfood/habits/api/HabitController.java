package com.familyfood.habits.api;

import com.familyfood.habits.application.HabitConsistencyView;
import com.familyfood.habits.application.HabitService;
import com.familyfood.habits.application.HabitView;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Habits and their daily log (D-034 Phase 2).
 *
 * <p>Requires sign-in; not in {@code SecurityConfig}'s permit-all list. Authorization lives in
 * {@link HabitService} and deliberately not here.
 *
 * <p>There is no streak, badge, points, leaderboard or ranking route, and no route that notifies
 * a child. Those are absent by decision (D-036, CH-004), not pending — the data to build them
 * from is not stored and not computed.
 */
@RestController
@RequestMapping("/api/v1/habits")
@Tag(name = "Habits")
public class HabitController {

    private final HabitService habits;
    private final CurrentUser currentUser;

    public HabitController(HabitService habits, CurrentUser currentUser) {
        this.habits = habits;
        this.currentUser = currentUser;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start tracking a habit for a household member")
    public HabitView create(@Valid @RequestBody CreateHabitRequest request) {
        UUID accountId = currentUser.require().accountId();
        return habits.createHabit(accountId, request.memberId(), request.name(), request.cadence());
    }

    @GetMapping
    @Operation(summary = "Habits for one member, newest first")
    public List<HabitView> list(@RequestParam UUID memberId,
                                @RequestParam(defaultValue = "false") boolean includeArchived) {
        return habits.listHabits(currentUser.require().accountId(), memberId, includeArchived);
    }

    @PutMapping("/{habitId}")
    @Operation(summary = "Rename or re-pace a habit. Its completion log is untouched.")
    public HabitView update(@PathVariable UUID habitId, @Valid @RequestBody UpdateHabitRequest request) {
        UUID accountId = currentUser.require().accountId();
        return habits.updateHabit(accountId, habitId, request.name(), request.cadence());
    }

    @PostMapping("/{habitId}/archive")
    @Operation(summary = "Stop tracking a habit",
               description = "Nothing is deleted. The completion log survives, because a member "
                           + "who gave something up still did it for six weeks.")
    public HabitView archive(@PathVariable UUID habitId) {
        return habits.archiveHabit(currentUser.require().accountId(), habitId);
    }

    @PostMapping("/{habitId}/reactivate")
    @Operation(summary = "Resume tracking an archived habit")
    public HabitView reactivate(@PathVariable UUID habitId) {
        return habits.reactivateHabit(currentUser.require().accountId(), habitId);
    }

    /**
     * PUT on the day itself, so the route is idempotent by construction: the same request twice
     * states the same fact twice, which is what a double tap or a retry actually is.
     */
    @PutMapping("/{habitId}/completions/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Mark a day done. Repeating the request is success, not a conflict.")
    public void complete(@PathVariable UUID habitId, @PathVariable LocalDate date) {
        habits.complete(currentUser.require().accountId(), habitId, date);
    }

    @DeleteMapping("/{habitId}/completions/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a day. Also idempotent — a day that was never marked is "
                       + "already in the state the caller asked for.")
    public void uncomplete(@PathVariable UUID habitId, @PathVariable LocalDate date) {
        habits.uncomplete(currentUser.require().accountId(), habitId, date);
    }

    @GetMapping("/{habitId}/completions")
    @Operation(summary = "The days this habit happened, newest first. Defaults to the last week.")
    public List<LocalDate> history(@PathVariable UUID habitId,
                                  @RequestParam(required = false) LocalDate from,
                                  @RequestParam(required = false) LocalDate to) {
        return habits.history(currentUser.require().accountId(), habitId, from, to);
    }

    @GetMapping("/{habitId}/consistency")
    @Operation(summary = "How many of the expected days in a window were logged",
               description = "Two counts and the window they were counted over, clamped to the "
                           + "habit's own life and never into the future. No percentage, streak "
                           + "or grade — this is a fact about the log, not a score (D-036).")
    public HabitConsistencyView consistency(@PathVariable UUID habitId,
                                            @RequestParam(required = false) LocalDate from,
                                            @RequestParam(required = false) LocalDate to) {
        return habits.consistency(currentUser.require().accountId(), habitId, from, to);
    }
}
