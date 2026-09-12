package com.familyfood.habits.application;

import com.familyfood.audit.AuditAction;
import com.familyfood.audit.AuditService;
import com.familyfood.habits.domain.Habit;
import com.familyfood.habits.domain.HabitCadence;
import com.familyfood.habits.domain.HabitCompletion;
import com.familyfood.habits.infra.HabitCompletionRepository;
import com.familyfood.habits.infra.HabitRepository;
import com.familyfood.household.application.HouseholdAccess;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;
import com.familyfood.platform.error.AppException;
import com.familyfood.platform.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Habits, and the log of the days they happened.
 *
 * <h2>What this module refuses to be</h2>
 * There is no streak, badge, point, level, rank, leaderboard, comparison between members or
 * notification aimed at a child anywhere in this module — not stored, not computed, not
 * returned. That is a product boundary, not a backlog (D-036, CH-004): the V13 tables carry no
 * such column, the entities carry no such field, and {@link #consistency} returns two counts and
 * the window it counted over. "4 of 5 expected days" is a fact about the log; "a 12-day streak"
 * is a score, and a score aimed at a child's behaviour is the thing this product does not build.
 *
 * <p>Consistency is therefore computed at read time rather than maintained. A stored counter
 * would need a job to keep it true, would disagree with the log the first time that job failed,
 * and would be a streak under a different name.
 *
 * <h2>Honesty about the window</h2>
 * Consistency is clamped to the habit's own life and never counts into the future. A habit
 * created on Friday has not failed the four days before it existed, and a 30-day window does not
 * expect the 20 days that have not happened yet. Without that clamp the first thing a new user
 * sees is a number that says they are already behind.
 *
 * <p>Authorization is the same model as observations and grants nothing to a household role:
 * self, or guardian of a child member (D-038).
 */
@Service
public class HabitService {

    private static final String RESOURCE_TYPE_HABIT = "HABIT";
    private static final String CHILD_MEMBER_TYPE = "CHILD";

    /** Matches the {@code habit} name CHECK, so a 90-character name is a 400 and not a 500. */
    private static final int NAME_MAX_LENGTH = 80;

    /** A week: the window a member means when they ask how a habit is going. */
    private static final int DEFAULT_WINDOW_DAYS = 7;

    private final HabitRepository habits;
    private final HabitCompletionRepository completions;
    private final HouseholdAccess households;
    private final AuditService audit;
    private final Clock clock;

    public HabitService(HabitRepository habits,
                        HabitCompletionRepository completions,
                        HouseholdAccess households,
                        AuditService audit,
                        Clock clock) {
        this.habits = habits;
        this.completions = completions;
        this.households = households;
        this.audit = audit;
        this.clock = clock;
    }

    /* ------------------------------------------------------------------- lifecycle */

    @Transactional
    public HabitView createHabit(UUID callerAccountId, UUID memberId, String name, HabitCadence cadence) {
        MemberInfo subject = requireMember(memberId);
        requireSelfOrGuardian(callerAccountId, subject);

        Habit saved = habits.save(Habit.create(
                subject.id(), requireName(name), requireCadence(cadence), callerAccountId, clock.instant()));

        if (CHILD_MEMBER_TYPE.equals(subject.memberType())) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_HABIT,
                    saved.getId(), callerAccountId, subject.householdId());
        }
        audit.success(AuditAction.HABIT_CREATED, RESOURCE_TYPE_HABIT,
                saved.getId(), callerAccountId, subject.householdId());

        return HabitView.from(saved);
    }

    @Transactional
    public HabitView updateHabit(UUID callerAccountId, UUID habitId, String name, HabitCadence cadence) {
        Habit habit = requireHabit(habitId);
        MemberInfo subject = requireSubjectOf(callerAccountId, habit);

        habit.update(requireName(name), requireCadence(cadence));
        Habit saved = habits.save(habit);

        audit.success(AuditAction.HABIT_UPDATED, RESOURCE_TYPE_HABIT,
                saved.getId(), callerAccountId, subject.householdId());

        return HabitView.from(saved);
    }

    /** Stops tracking without deleting. The completion log survives, because a member who gave
     *  something up still did it for six weeks. */
    @Transactional
    public HabitView archiveHabit(UUID callerAccountId, UUID habitId) {
        Habit habit = requireHabit(habitId);
        MemberInfo subject = requireSubjectOf(callerAccountId, habit);

        habit.archive(clock.instant());
        Habit saved = habits.save(habit);

        audit.success(AuditAction.HABIT_ARCHIVED, RESOURCE_TYPE_HABIT,
                saved.getId(), callerAccountId, subject.householdId());

        return HabitView.from(saved);
    }

    @Transactional
    public HabitView reactivateHabit(UUID callerAccountId, UUID habitId) {
        Habit habit = requireHabit(habitId);
        MemberInfo subject = requireSubjectOf(callerAccountId, habit);

        habit.reactivate();
        Habit saved = habits.save(habit);

        audit.success(AuditAction.HABIT_REACTIVATED, RESOURCE_TYPE_HABIT,
                saved.getId(), callerAccountId, subject.householdId());

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
        MemberInfo subject = requireSubjectOf(callerAccountId, habit);
        LocalDate day = requireNotFuture(on);

        if (!habit.isActive()) {
            // Logging new activity against something the member stopped tracking is almost
            // always a stale client rather than an intention. Removing an entry stays allowed.
            throw AppException.validation("habitId", "habit is archived");
        }

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

        if (CHILD_MEMBER_TYPE.equals(subject.memberType())) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_HABIT,
                    habitId, callerAccountId, subject.householdId());
        }
        audit.success(AuditAction.HABIT_COMPLETION_RECORDED, RESOURCE_TYPE_HABIT,
                habitId, callerAccountId, subject.householdId());
    }

    /** Removes a day. Idempotent for the same reason {@link #complete} is — an un-tick of an
     *  untouched day is already the state the caller wants. */
    @Transactional
    public void uncomplete(UUID callerAccountId, UUID habitId, LocalDate on) {
        Habit habit = requireHabit(habitId);
        MemberInfo subject = requireSubjectOf(callerAccountId, habit);
        LocalDate day = requireDate(on);

        completions.deleteByHabitIdAndCompletedOn(habitId, day);

        audit.success(AuditAction.HABIT_COMPLETION_REMOVED, RESOURCE_TYPE_HABIT,
                habitId, callerAccountId, subject.householdId());
    }

    /* ---------------------------------------------------------------------- reading */

    @Transactional(readOnly = true)
    public List<HabitView> listHabits(UUID callerAccountId, UUID memberId, boolean includeArchived) {
        MemberInfo subject = requireMember(memberId);
        requireSelfOrGuardian(callerAccountId, subject);
        auditCrossMemberRead(callerAccountId, subject);

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
        MemberInfo subject = requireSubjectOf(callerAccountId, habit);
        auditCrossMemberRead(callerAccountId, subject);

        LocalDate end = to == null ? LocalDate.now(clock) : to;
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
        MemberInfo subject = requireSubjectOf(callerAccountId, habit);
        auditCrossMemberRead(callerAccountId, subject);

        LocalDate today = LocalDate.now(clock);
        LocalDate requestedEnd = to == null ? today : to;
        LocalDate requestedStart = from == null ? requestedEnd.minusDays(DEFAULT_WINDOW_DAYS - 1L) : from;

        // Never before the habit existed, and never past today or the day tracking stopped.
        LocalDate bornOn = LocalDate.ofInstant(habit.getCreatedAt(), clock.getZone());
        LocalDate lastTrackedDay = habit.getArchivedAt() == null
                ? today
                : LocalDate.ofInstant(habit.getArchivedAt(), clock.getZone());

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

    /* -------------------------------------------------------------------- erasure */

    /** Called by account erasure (D-028). Completions cascade from {@code habit} in the DB. */
    @Transactional
    public void eraseAllForMember(UUID subjectMemberId) {
        habits.deleteByHouseholdMemberId(subjectMemberId);
    }

    /* ------------------------------------------------------------------ internals */

    private Habit requireHabit(UUID habitId) {
        return habits.findById(habitId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private MemberInfo requireMember(UUID memberId) {
        return households.findMember(memberId)
                .filter(MemberInfo::active)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    /** Resolves the habit's subject and checks the caller may act on their behalf. */
    private MemberInfo requireSubjectOf(UUID callerAccountId, Habit habit) {
        MemberInfo subject = requireMember(habit.getHouseholdMemberId());
        requireSelfOrGuardian(callerAccountId, subject);
        return subject;
    }

    private boolean isSelf(UUID callerAccountId, MemberInfo subject) {
        return callerAccountId.equals(subject.userAccountId());
    }

    /**
     * Who may keep or read a habit: the subject, or a guardian of a child subject. Not another
     * adult in the household, however senior — an adult is their own data principal (D-002), and
     * household role grants nothing here (D-038).
     */
    private void requireSelfOrGuardian(UUID callerAccountId, MemberInfo subject) {
        if (isSelf(callerAccountId, subject)) {
            return;
        }
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())
                && households.isGuardianOf(callerAccountId, subject.id())) {
            return;
        }
        audit.denied(AuditAction.HEALTH_DATA_ACCESS_DENIED, RESOURCE_TYPE_HABIT,
                subject.id(), callerAccountId, subject.householdId(),
                "caller is neither the subject nor a guardian of this subject");
        throw new AppException(ErrorCode.ACCESS_DENIED);
    }

    /** Reading someone else's habits leaves a trace, as reading their observations does. */
    private void auditCrossMemberRead(UUID callerAccountId, MemberInfo subject) {
        if (isSelf(callerAccountId, subject)) {
            return;
        }
        if (CHILD_MEMBER_TYPE.equals(subject.memberType())) {
            audit.success(AuditAction.CHILD_DATA_ACCESSED, RESOURCE_TYPE_HABIT,
                    subject.id(), callerAccountId, subject.householdId());
        }
        audit.success(AuditAction.HEALTH_DATA_ACCESSED, RESOURCE_TYPE_HABIT,
                subject.id(), callerAccountId, subject.householdId());
    }

    /** Trimmed here so the stored value matches what the CHECK constraint measures. */
    private String requireName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty() || trimmed.length() > NAME_MAX_LENGTH) {
            throw AppException.validation("name", "must be 1 to " + NAME_MAX_LENGTH + " characters");
        }
        return trimmed;
    }

    private HabitCadence requireCadence(HabitCadence cadence) {
        if (cadence == null) {
            throw AppException.validation("cadence", "must be present");
        }
        return cadence;
    }

    private LocalDate requireDate(LocalDate on) {
        if (on == null) {
            throw AppException.validation("completedOn", "must be present");
        }
        return on;
    }

    /** A habit cannot have happened tomorrow. Backfilling a past day stays allowed, because
     *  remembering on Tuesday that you did it on Monday is normal. */
    private LocalDate requireNotFuture(LocalDate on) {
        LocalDate day = requireDate(on);
        if (day.isAfter(LocalDate.now(clock))) {
            throw AppException.validation("completedOn", "cannot be in the future");
        }
        return day;
    }

    private static LocalDate earliest(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }
}
