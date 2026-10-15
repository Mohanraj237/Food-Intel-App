package com.familyfood.access.infra;

import com.familyfood.access.domain.AccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Private to the access module. Every other module learns what it may see through
 * {@code HealthAccessPolicy}, never by reading grants — a second reader of this table would be
 * a second interpretation of it (D-044).
 */
public interface AccessGrantRepository extends JpaRepository<AccessGrant, UUID> {

    /** Live grants from one subject to one audience member — the authorization lookup. */
    List<AccessGrant> findBySubjectMemberIdAndAudienceMemberIdAndRevokedAtIsNull(
            UUID subjectMemberId, UUID audienceMemberId);

    /** Everything a subject currently shares, for their own privacy screen. */
    List<AccessGrant> findBySubjectMemberIdAndRevokedAtIsNullOrderByGrantedAtDesc(UUID subjectMemberId);

    /** Every grant a subject ever made, revoked ones included — for their data export. */
    List<AccessGrant> findBySubjectMemberIdOrderByGrantedAtDesc(UUID subjectMemberId);

    /** What has been shared with any of these memberships — "shared with me". */
    List<AccessGrant> findByAudienceMemberIdInAndRevokedAtIsNull(Collection<UUID> audienceMemberIds);

    List<AccessGrant> findByAudienceMemberIdAndRevokedAtIsNull(UUID audienceMemberId);

    Optional<AccessGrant> findByIdAndSubjectMemberId(UUID id, UUID subjectMemberId);

    /** Erasure of the subject's own data (D-028): their grants go with it. */
    @Modifying(flushAutomatically = true)
    @Query("delete from AccessGrant g where g.subjectMemberId = :subjectMemberId")
    void deleteBySubjectMemberId(@Param("subjectMemberId") UUID subjectMemberId);
}
