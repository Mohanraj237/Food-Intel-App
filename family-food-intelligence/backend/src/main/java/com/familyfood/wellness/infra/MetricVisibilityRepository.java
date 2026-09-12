package com.familyfood.wellness.infra;

import com.familyfood.wellness.domain.MetricVisibilityGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MetricVisibilityRepository extends JpaRepository<MetricVisibilityGrant, UUID> {

    /** Live grants naming this audience — the read-authorization lookup. */
    List<MetricVisibilityGrant> findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(
            UUID subjectMemberId, UUID audienceMemberId);

    /** Everything a subject currently shares, for their own privacy screen. */
    List<MetricVisibilityGrant> findBySubjectMemberIdAndRevokedAtIsNull(UUID subjectMemberId);

    Optional<MetricVisibilityGrant> findByIdAndSubjectMemberId(UUID id, UUID subjectMemberId);

    void deleteBySubjectMemberId(UUID subjectMemberId);
}
