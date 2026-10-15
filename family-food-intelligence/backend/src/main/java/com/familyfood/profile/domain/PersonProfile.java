package com.familyfood.profile.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * One version of a household member's profile.
 *
 * <p><b>Versioned, never overwritten.</b> An update never mutates this row; it supersedes
 * it and inserts a new one with {@code version + 1}. {@link #supersede} is the only way an
 * existing row changes after creation. The partial unique index
 * {@code uq_profile_current} (one {@code is_current} row per {@code householdMemberId}) is
 * the database's half of that guarantee; {@link #supersede} and the factory methods here
 * are the code's half, kept in sync deliberately (mirrors the style of
 * {@code ConsentRecord}).
 *
 * <p><b>Modelling choice for goals/allergens/conditions.</b> The schema gives each of
 * these its own table with a composite primary key of {@code (profile_id, code)} and no
 * columns beyond that pair (goal, allergen) or a single extra default-valued column
 * (condition's {@code self_declared}, which this module always leaves at its database
 * default of {@code true} — there is no pathway yet, and none requested for this task, by
 * which a condition could be recorded as anything but self-declared). Modelling each as a
 * dedicated {@code @Entity} with a composite {@code @EmbeddedId} would be the more
 * "textbook" choice, but for a value that is nothing more than a closed set of codes
 * hanging off a version, that is pure ceremony: three extra entity classes, three extra
 * repositories, no behaviour of their own. A {@code @ElementCollection} of the code (an
 * enum for goals and allergens, a validated {@code String} for the free-text condition
 * code) maps to exactly the same tables and rows, is simpler to read, and — because
 * {@code PersonProfile} is itself an immutable-once-created version — carries none of the
 * usual "element collections make dirty-checking surprising" risk: a version's collections
 * are written once, at construction, and never mutated again.
 *
 * <p>Collections are fetched eagerly. They are bounded (at most a handful of goals, at
 * most the size of the allergen reference table, a small number of free-text conditions)
 * and {@code spring.jpa.open-in-view} is disabled, so a lazy collection would simply throw
 * once the request DTO tries to read it outside the transaction.
 */
@Entity
@Table(name = "person_profile")
public class PersonProfile {

    @Id
    private UUID id;

    @Column(name = "household_member_id", nullable = false)
    private UUID householdMemberId;

    @Column(nullable = false)
    private int version;

    @Column(name = "is_current", nullable = false)
    private boolean isCurrent;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_band", nullable = false)
    private AgeBand ageBand;

    @Enumerated(EnumType.STRING)
    @Column
    private Sex sex;

    @Enumerated(EnumType.STRING)
    @Column(name = "dietary_preference")
    private DietaryPreference dietaryPreference;

    /** Self-reported; never an input to an energy target (D-047). Nullable. */
    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level")
    private ActivityLevel activityLevel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "superseded_at")
    private Instant supersededAt;

    @Column(name = "created_by_user_id", nullable = false)
    private UUID createdByUserId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_goal", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "goal_code")
    private Set<GoalCode> goals = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_allergen", joinColumns = @JoinColumn(name = "profile_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "allergen_code")
    private Set<AllergenCode> allergens = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_condition", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "condition_code")
    private Set<String> conditions = new LinkedHashSet<>();

    protected PersonProfile() {
        // JPA
    }

    /**
     * Builds the next version for a household member.
     *
     * @param version 1 for a brand-new member, or {@code previous.getVersion() + 1} when
     *                superseding an existing current version. The caller — not this
     *                method — decides which, because only the caller has queried for an
     *                existing current row.
     */
    public static PersonProfile newVersion(UUID householdMemberId,
                                           int version,
                                           AgeBand ageBand,
                                           Sex sex,
                                           DietaryPreference dietaryPreference,
                                           Set<GoalCode> goals,
                                           Set<AllergenCode> allergens,
                                           Set<String> conditions,
                                           UUID createdByUserId,
                                           Instant now) {
        return newVersion(householdMemberId, version, ageBand, sex, dietaryPreference, null,
                goals, allergens, conditions, createdByUserId, now);
    }

    /** As above, with the self-reported activity level added in V14. */
    public static PersonProfile newVersion(UUID householdMemberId,
                                           int version,
                                           AgeBand ageBand,
                                           Sex sex,
                                           DietaryPreference dietaryPreference,
                                           ActivityLevel activityLevel,
                                           Set<GoalCode> goals,
                                           Set<AllergenCode> allergens,
                                           Set<String> conditions,
                                           UUID createdByUserId,
                                           Instant now) {
        if (version < 1) {
            // Mirrors the ck (version >= 1) CHECK. Kept here too for the same reason as
            // ConsentRecord's mirrored invariant: an early, legible failure.
            throw new IllegalArgumentException("version must be >= 1");
        }
        if (ageBand == null) {
            throw new IllegalArgumentException("ageBand is required");
        }
        for (String code : conditions == null ? Set.<String>of() : conditions) {
            if (code == null || code.isBlank() || code.trim().length() > 60) {
                // Mirrors ck: length(btrim(condition_code)) BETWEEN 1 AND 60.
                throw new IllegalArgumentException(
                        "condition code must be non-blank and at most 60 characters");
            }
        }

        PersonProfile profile = new PersonProfile();
        profile.id = UUID.randomUUID();
        profile.householdMemberId = householdMemberId;
        profile.version = version;
        profile.isCurrent = true;
        profile.ageBand = ageBand;
        profile.sex = sex;
        profile.dietaryPreference = dietaryPreference;
        profile.activityLevel = activityLevel;
        profile.createdAt = now;
        profile.createdByUserId = createdByUserId;
        profile.goals = goals == null ? new LinkedHashSet<>() : new LinkedHashSet<>(goals);
        profile.allergens = allergens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(allergens);
        profile.conditions = conditions == null
                ? new LinkedHashSet<>()
                : conditions.stream().map(String::trim).collect(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        return profile;
    }

    /**
     * Retires this version. Mirrors {@code ck_profile_current_not_superseded}: a row is
     * either current with no supersession timestamp, or superseded with one — never both
     * states at once, and never neither once this has been called.
     */
    public void supersede(Instant now) {
        if (!isCurrent) {
            return;
        }
        this.isCurrent = false;
        this.supersededAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getHouseholdMemberId() {
        return householdMemberId;
    }

    public int getVersion() {
        return version;
    }

    public boolean isCurrent() {
        return isCurrent;
    }

    public AgeBand getAgeBand() {
        return ageBand;
    }

    public Sex getSex() {
        return sex;
    }

    public DietaryPreference getDietaryPreference() {
        return dietaryPreference;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Set<GoalCode> getGoals() {
        return Set.copyOf(goals);
    }

    public Set<AllergenCode> getAllergens() {
        return Set.copyOf(allergens);
    }

    public Set<String> getConditions() {
        return Set.copyOf(conditions);
    }
}
