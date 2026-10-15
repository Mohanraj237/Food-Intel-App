package com.familyfood.access.application;

import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;
import com.familyfood.access.domain.Relationship;
import com.familyfood.household.application.HouseholdAccess.MemberInfo;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What one actor may do with one subject's data, as decided by {@link HealthAccessPolicy}.
 *
 * <p>An immutable answer computed once per request, so that a screen showing several kinds of
 * data about a person asks the question once and cannot get two different answers.
 */
public record AccessDecision(MemberInfo subject,
                             Relationship relationship,
                             Map<AccessScope, AccessLevel> levels) {

    private static final String CHILD_MEMBER_TYPE = "CHILD";

    public AccessDecision {
        EnumMap<AccessScope, AccessLevel> copy = new EnumMap<>(AccessScope.class);
        copy.putAll(levels);
        levels = Collections.unmodifiableMap(copy);
    }

    /** Everything, at MANAGE — what SELF and GUARDIAN hold. */
    static Map<AccessScope, AccessLevel> everything() {
        Map<AccessScope, AccessLevel> all = new EnumMap<>(AccessScope.class);
        for (AccessScope scope : AccessScope.values()) {
            all.put(scope, AccessLevel.MANAGE);
        }
        return all;
    }

    public boolean canView(AccessScope scope) {
        return levels.containsKey(scope);
    }

    public boolean canManage(AccessScope scope) {
        return levels.get(scope) == AccessLevel.MANAGE;
    }

    public Set<AccessScope> viewableScopes() {
        return levels.isEmpty() ? EnumSet.noneOf(AccessScope.class) : EnumSet.copyOf(levels.keySet());
    }

    public boolean isSelf() {
        return relationship == Relationship.SELF;
    }

    /** True whenever the actor is looking at someone else — every such access is audited. */
    public boolean isCrossMember() {
        return relationship != Relationship.SELF;
    }

    public boolean isChildSubject() {
        return CHILD_MEMBER_TYPE.equals(subject.memberType());
    }

    public UUID subjectId() {
        return subject.id();
    }

    public UUID householdId() {
        return subject.householdId();
    }
}
