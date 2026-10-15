package com.familyfood.habits.application;

import com.familyfood.access.application.AccessDecision;
import com.familyfood.access.application.HealthAccessPolicy;
import com.familyfood.access.application.TrackingConsent;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.habits.domain.Habit;
import com.familyfood.habits.domain.HabitCadence;
import com.familyfood.habits.domain.HabitCompletion;
import com.familyfood.habits.infra.HabitCompletionRepository;
import com.familyfood.habits.infra.HabitRepository;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.HouseholdInfo;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Habits, and the log of the days they happened.
 *
 * <h2>What this module refuses to be</h2>
 * There is no streak, badge, point, level, rank, leaderboard, comparison between members or
 * notification anywhere in this module — not stored, not computed, not returned. For a child
 * that is permanent (D-036, CH-004); for adults, any engagement mechanic is a separate, opt-in,
 * cooperative feature governed by D-051 and would not live here. {@link #consistency} returns
 * two counts and the window it counted over. "4 of 5 expected days" is a fact about the log;
 * "a 12-day streak" is a score.
 *
 * <p>Consistency is therefore computed at read time rather than maintained. A stored counter
 * would need a job to keep it true, would disagree with the log the first time that job failed,
 * and would be a streak under a different name.
 *
 * <h2>Honesty about the window</h2>
 * Consistency is clamped to the habit's own life and never counts into the future. A habit
 * created on Friday has not failed the four days before it existed, and a 30-day window does not
 * expect the 20 days that have not happened yet. Days are the household's days (D-053): ticking
 * today's habit at 00:30 in India is ticking today, not "a day in the future".
 *
 * <p>Access is the one policy (D-044) on the HABITS scope: the subject, a guardian of a child, or
 * someone the subject granted HABITS to. Writes need MANAGE and the subject's tracking consent
 * (D-048); reads need VIEW.
 */
@Service
public class HabitService {

    private static final String RESOURCE_TYPE_HABIT = "HABIT";

    /** Matches the {@code habit} name CHECK, so a 90-character name is a 400 and not a 500. */
    private static final int NAME_MAX_LENGTH = 80;

    /** A week: the window a member means when they ask how a habit is going. */
    private static final int DEFAULT_WINDOW_DAYS = 7;

    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final HealthAccessPolicy access;
    private final TrackingConsent trackingConsent;
    private final HouseholdAccess households;
    private final AuditService audit;
    private final Clock clock;

    public HabitService(HabitRepository habits,
                        HabitCompletionRepository completions,
                        HealthAccessPolicy access,
                        TrackingConsent trackingConsent,
                        HouseholdAccess households,
                        AuditService audit,
                        Clock clock) {
        this.habits = habits;
        this.completions = completions;
        this.access = access;
        this.trackingConsent = trackingConsent;
        this.households = households;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------- lifecycle */

    @Transactional
    public HabitView createHabit(UUID callerAccountId, UUID memberId, String name, HabitCadence cadence) {
        String validName = requireName(name);
        HabitCadence validCadence = requireCadence(cadence);
        AccessDecision decision = access.requireManage(callerAccountId, memberId, AccessScope.HABITS,
                RESOURCE_TYPE_HABIT);
        trackingConsent.require(decision.subject());

        Habit saved = habits.save(Habit.create(
                decision.subjectId(), validName, validCadence, callerAccountId, clock.instant()));

        auditWrite(callerAccountId, decision, AuditAction.HABIT_CREATED, saved.getId());
        return HabitView.from(saved);
    }

    @Transactional
    public HabitView updateHabit(UUID callerAccountId, UUID habitId, String name, HabitCadence cadence) {
        Habit habit = requireHabit(habitId);
        AccessDecision decision = requireManage(callerAccountId, habit);

        habit.update(requireName(name), requireCadence(cadence));
        Habit saved = habits.save(habit);

        auditWrite(callerAccountId, decision, AuditAction.HABIT_UPDATED, saved.getId());
        return HabitView.from(saved);
    }

    /** Stops tracking without deleting. The completion log survives, because a member who gave
     *  something up still did it for six weeks. */
    @Transactional
    public HabitView archiveHabit(UUID callerAccountId, UUID habitId) {
        Habit habit = requireHabit(habitId);
        AccessDecision decision = requireManage(callerAccountId, habit);

        habit.archive(clock.instant());
        Habit saved = habits.save(habit);

        auditWrite(callerAccountId, decision, AuditAction.HABIT_ARCHIVED, saved.getId());
        return HabitView.from(saved);
    }

    @Transactional
    public HabitView reactivateHabit(UUID callerAccountId, UUID habitId) {
        Habit habit = requireHabit(habitId);
        AccessDecision decision = requireManage(callerAccountId, habit);
        trackingConsent.require(decision.subject());

        habit.reactivate();
        Habit saved = habits.save(habit);

        auditWrite(callerAccountId, decision, AuditAction.HABIT_REACTIVATED, saved.getId());
        return HabitView.from(saved);
    }

    /* ----------------------------------------------------------------- the daily log */

    /**
     * Marks a day done. Idempotent, in two layers.
     *
     * <p>The lookup handles the ordinary repeat — a double tap, a second device, a refresh — and
     * the caught constraint violation handles two requests racing, because the unique index is
     * the only thing that can actually arbitrate that. Either way a repeat is success: saying
     * "you already did this" to someone who just told you they did it is a worse outcome than
     * doing nothing.
     */
    @Transactional
    public void complete(UUID callerAccountId, UUID habitId, LocalDate on) {
        Habit habit = requireHabit(habitId);
        AccessDecision decision = requireManage(callerAccountId, habit);
        LocalDate day = requireNotFuture(on, decision.subject());

        if (!habit.isActive()) {
            // Logging new activity against something the member stopped tracking is almost
            // always a stale client rather than an intention. Removing an entry stays allowed.
            throw AppException.validation("habitId", "habit is archived");
        }
        trackingConsent.require(decision.subject());

        if (completions.findByHabitIdAndCompletedOn(habitId, day).isPresent()) {
            return;
        }

        try {
            completions.save(HabitCompletion.on(habitId, day, callerAccountId, clock.instant()));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with another request for the same day. The fact is recorded, which is
            // all the caller asked for.
            return;
        }

        auditWrite(callerAccountId, decision, AuditAction.HABIT_COMPLETION_RECORDED, habitId);
    }

    /** Removes a day. Idempotent for the same reason {@link #complete} is — an un-tick of an
     *  untouched day is already the state the caller wants. */
    @Transactional
    public void uncomplete(UUID callerAccountId, UUID habitId, LocalDate on) {
        Habit habit = requireHabit(habitId);
        AccessDecision decision = requireManage(callerAccountId, habit);
        LocalDate day = requireDate(on);

        completions.deleteByHabitIdAndCompletedOn(habitId, day);

        auditWrite(callerAccountId, decision, AuditAction.HABIT_COMPLETION_REMOVED, habitId);
    }

    /* ---------------------------------------------------------------------- reading */

    @Transactional(readOnly = true)
    public List<HabitView> listHabits(UUID callerAccountId, UUID memberId, boolean includeArchived) {
        access.requireView(callerAccountId, memberId, AccessScope.HABITS, RESOURCE_TYPE_HABIT);

        List<Habit> found = includeArchived
                ? habits.findByHouseholdMemberIdOrderByCreatedAtDesc(memberId)
                : habits.findByHouseholdMemberIdAndActiveTrueOrderByCreatedAtDesc(memberId);

        return found.stream().map(HabitView::from).toList();
    }

    /**
     * The days a habit happened, newest first.
     *
     * <p>Dates, not a rate and not a run length — enough for a client to draw a calendar and
     * nothing from which a streak could be reconstructed as a product feature.
     */
    @Transactional(readOnly = true)
    public List<LocalDate> history(UUID callerAccountId, UUID habitId, LocalDate from, LocalDate to) {
        Habit habit = requireHabit(habitId);
        AccessDecision decision = access.requireView(callerAccountId, habit.getHouseholdMemberId(),
                AccessScope.HABITS, RESOURCE_TYPE_HABIT);

        LocalDate end = to == null ? today(decision.subject()) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_WINDOW_DAYS - 1L) : from;
        if (end.isBefore(start)) {
            return List.of();
        }

        return completions.findByHabitIdAndCompletedOnBetweenOrderByCompletedOnDesc(habitId, start, end)
                .stream()
                .map(HabitCompletion::getCompletedOn)
                .toList();
    }

    /**
     * "N of M expected days" for a window, computed now from the log and the cadence.
     *
     * <p>The window returned is the window actually measured, which may be narrower than the one
     * asked for — see {@link HabitConsistencyView}. Reporting the clamped window rather than the
     * requested one is the difference between a checkable statement and a ratio whose denominator
     * the reader has to guess.
     */
    @Transactional(readOnly = true)
    public HabitConsistencyView consistency(UUID callerAccountId, UUID habitId, LocalDate from, LocalDate to) {
        Habit habit = requireHabit(habitId);
        AccessDecision decision = access.requireView(callerAccountId, habit.getHouseholdMemberId(),
                AccessScope.HABITS, RESOURCE_TYPE_HABIT);
        ZoneId zone = zoneOf(decision.subject());

        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate requestedEnd = to == null ? today : to;
        LocalDate requestedStart = from == null ? requestedEnd.minusDays(DEFAULT_WINDOW_DAYS - 1L) : from;

        // Never before the habit existed, and never past today or the day tracking stopped.
        LocalDate bornOn = LocalDate.ofInstant(habit.getCreatedAt(), zone);
        LocalDate lastTrackedDay = habit.getArchivedAt() == null
                ? today
                : LocalDate.ofInstant(habit.getArchivedAt(), zone);

        LocalDate start = requestedStart.isBefore(bornOn) ? bornOn : requestedStart;
        LocalDate end = earliest(earliest(requestedEnd, today), lastTrackedDay);

        if (end.isBefore(start)) {
            // The requested window and the habit's life do not overlap. Zero of zero is the
            // honest answer: no days were expected, so none were missed.
            return new HabitConsistencyView(habitId, start, start, 0, 0);
        }

        int completedDays = (int) completions.countByHabitIdAndCompletedOnBetween(habitId, start, end);
        int expectedDays = habit.getCadence().expectedDays(start, end);

        return new HabitConsistencyView(habitId, start, end, completedDays, expectedDays);
    }

    /**
     * Which of a member's live habits were done on one day, for a day summary. The caller has
     * already been authorized for HABITS. One day of {@link #daySummaries}, so a day summary and
     * the same day in a week can never disagree.
     */
    @Transactional(readOnly = true)
    public DaySummary daySummary(AccessDecision authorized, LocalDate day) {
        HabitDay d = daySummaries(authorized, day, day, zoneOf(authorized.subject())).get(0);
        return new DaySummary(d.active(), d.due(), d.done());
    }

    /**
     * A member's live habits day by day across a window, for a week strip — two queries however
     * many habits and days there are. The caller has already been authorized for HABITS.
     *
     * <p>A habit counts only from the day it was created, the same clamp {@link #consistency}
     * applies: a habit started on Friday was not due, and was not left undone, on Tuesday.
     *
     * @param zone the subject's household zone (D-053), which decides the day a habit was created
     * @return one entry per day from {@code from} to {@code to} inclusive, oldest first
     */
    @Transactional(readOnly = true)
    public List<HabitDay> daySummaries(AccessDecision authorized, LocalDate from, LocalDate to, ZoneId zone) {
        List<LocalDate> days = from.datesUntil(to.plusDays(1)).toList();
        List<Habit> live = habits.findByHouseholdMemberIdAndActiveTrueOrderByCreatedAtDesc(authorized.subjectId());
        if (live.isEmpty()) {
            return days.stream().map(d -> new HabitDay(d, 0, 0, 0)).toList();
        }
        Map<LocalDate, Set<UUID>> doneByDay = completions.findByHabitIdInAndCompletedOnBetween(
                        live.stream().map(Habit::getId).toList(), from, to).stream()
                .collect(Collectors.groupingBy(HabitCompletion::getCompletedOn,
                        Collectors.mapping(HabitCompletion::getHabitId, Collectors.toSet())));

        return days.stream().map(day -> {
            List<Habit> existing = live.stream()
                    .filter(h -> !LocalDate.ofInstant(h.getCreatedAt(), zone).isAfter(day))
                    .toList();
            int due = (int) existing.stream().filter(h -> h.getCadence().expectedDays(day, day) > 0).count();
            Set<UUID> ids = existing.stream().map(Habit::getId).collect(Collectors.toSet());
            int done = (int) doneByDay.getOrDefault(day, Set.of()).stream().filter(ids::contains).count();
            return new HabitDay(day, existing.size(), due, done);
        }).toList();
    }

    /**
     * @param active     live habits
     * @param dueToday   of those, how many the cadence expects on this day (WEEKDAYS skips weekends)
     * @param doneToday  how many were ticked for this day
     */
    public record DaySummary(int active, int dueToday, int doneToday) {}

    /**
     * One day of a member's habits: counts only — no streak, score or percentage (D-036).
     *
     * @param active habits that existed that day
     * @param due    of those, how many the cadence expected that day
     * @param done   how many were ticked for that day
     */
    public record HabitDay(LocalDate date, int active, int due, int done) {}

    /* -------------------------------------------------------------------- data rights */

    /** Called by account and child erasure (D-028). Completions cascade from {@code habit}. */
    @Transactional
    public void eraseAllForMember(UUID subjectMemberId) {
        habits.deleteByHouseholdMemberId(subjectMemberId);
    }

    /** Every habit and the days it was done, for the member's export. */
    @Transactional(readOnly = true)
    public List<HabitExport> exportForMember(UUID subjectMemberId) {
        return habits.findByHouseholdMemberIdOrderByCreatedAtDesc(subjectMemberId).stream()
                .map(h -> new HabitExport(HabitView.from(h),
                        completions.findByHabitIdOrderByCompletedOnDesc(h.getId()).stream()
                                .map(HabitCompletion::getCompletedOn)
                                .toList()))
                .toList();
    }

    public record HabitExport(HabitView habit, List<LocalDate> completedOn) {}

    /* ------------------------------------------------------------------ internals */

    private Habit requireHabit(UUID habitId) {
        return habits.findById(habitId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AccessDecision requireManage(UUID callerAccountId, Habit habit) {
        return access.requireManage(callerAccountId, habit.getHouseholdMemberId(), AccessScope.HABITS,
                RESOURCE_TYPE_HABIT);
    }

    /** D-053: the subject's household decides where a day starts. */
    private ZoneId zoneOf(MemberInfo subject) {
        return households.findHousehold(subject.householdId())
                .map(HouseholdInfo::zone)
                .orElse(ZoneId.of("Asia/Kolkata"));
    }

    private LocalDate today(MemberInfo subject) {
        return LocalDate.now(clock.withZone(zoneOf(subject)));
    }

    /** A habit write with the subject attached, so a guardian's or caregiver's tick shows in
     *  the subject's access log; a child's also lands in the strict child-data category. */
    private void auditWrite(UUID callerAccountId, AccessDecision decision, AuditAction action, UUID habitId) {
        Map<String, Object> metadata = Map.of(
                "scope", AccessScope.HABITS.name(), "relationship", decision.relationship().name());
        if (decision.isChildSubject()) {
            audit.successForSubject(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_HABIT, habitId,
                    callerAccountId, decision.householdId(), decision.subjectId(), metadata);
        }
        audit.successForSubject(action, RESOURCE_TYPE_HABIT, habitId,
                callerAccountId, decision.householdId(), decision.subjectId(), metadata);
    }

    /** Trimmed here so the stored value matches what the CHECK constraint measures. */
    private static String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw AppException.validation("name", "must be 1 to " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private static HabitCadence requireCadence(HabitCadence cadence) {
        if (cadence == null) {
            throw AppException.validation("cadence", "must be present");
        }
        return cadence;
    }

    private static LocalDate requireDate(LocalDate on) {
        if (on == null) {
            throw AppException.validation("completedOn", "must be present");
        }
        return on;
    }

    /** A habit cannot have happened tomorrow — tomorrow where the member lives (D-053).
     *  Backfilling a past day stays allowed, because remembering on Tuesday that you did it on
     *  Monday is normal. */
    private LocalDate requireNotFuture(LocalDate on, MemberInfo subject) {
        LocalDate day = requireDate(on);
        if (day.isAfter(today(subject))) {
            throw AppException.validation("completedOn", "cannot be in the future");
        }
        return day;
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
