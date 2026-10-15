package com.familyfood.access.application;

import com.familyfood.access.domain.AccessGrant;
import com.familyfood.access.domain.AccessLevel;
import com.familyfood.access.domain.AccessScope;

import java.time.Instant;
import java.util.UUID;

/**
 * One grant as a person sees it on their sharing screen: whose data, with whom, which scope,
 * at which level, and since when. Labels are the household display labels both people already
 * see in the member list, so nothing here discloses anything new.
 */
public record AccessGrantView(UUID id,
                              UUID subjectMemberId,
                              String subjectLabel,
                              UUID audienceMemberId,
                              String audienceLabel,
                              AccessScope scope,
                              AccessLevel level,
                              Instant grantedAt,
                              Instant revokedAt) {

    static AccessGrantView of(AccessGrant grant, String subjectLabel, String audienceLabel) {
        return new AccessGrantView(grant.getId(), grant.getSubjectMemberId(), subjectLabel,
                grant.getAudienceMemberId(), audienceLabel, grant.getScope(), grant.getLevel(),
                grant.getGrantedAt(), grant.getRevokedAt());
    }
}
