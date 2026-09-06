package com.familyfood.consent.infra;

import com.familyfood.consent.domain.ConsentRecord;
import com.familyfood.consent.domain.ConsentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    /**
     * The gate's only question. At most one row can match because of
     * {@code uq_consent_active_per_subject_purpose}.
     */
    @Query("""
           select c from ConsentRecord c
           where c.subjectMemberId = :subjectMemberId
             and c.purposeCode = :purposeCode
             and c.status = com.familyfood.consent.domain.ConsentStatus.ACTIVE
           """)
    Optional<ConsentRecord> findActive(@Param("subjectMemberId") UUID subjectMemberId,
                                       @Param("purposeCode") String purposeCode);

    /**
     * The most recent record of any status for a subject and purpose.
     *
     * <p>Used only to tell "never asked" from "asked and withdrawn" when reporting a
     * denial, never to decide whether processing is permitted.
     */
    @Query("""
           select c from ConsentRecord c
           where c.subjectMemberId = :subjectMemberId
             and c.purposeCode = :purposeCode
           order by c.createdAt desc
           """)
    List<ConsentRecord> findHistory(@Param("subjectMemberId") UUID subjectMemberId,
                                    @Param("purposeCode") String purposeCode);

    @Query("""
           select c from ConsentRecord c
           where c.subjectMemberId in :subjectMemberIds
             and c.status = com.familyfood.consent.domain.ConsentStatus.ACTIVE
           """)
    List<ConsentRecord> findActiveForSubjects(
            @Param("subjectMemberIds") Collection<UUID> subjectMemberIds);

    @Query("""
           select c from ConsentRecord c
           where c.subjectMemberId in :subjectMemberIds
           order by c.createdAt desc
           """)
    List<ConsentRecord> findAllForSubjects(
            @Param("subjectMemberIds") Collection<UUID> subjectMemberIds);

    /**
     * Scoped by the granting account, not by id alone: a consent record is only ever
     * addressable by the person who granted it.
     */
    @Query("""
           select c from ConsentRecord c
           where c.id = :id and c.grantedByUserId = :grantedByUserId
           """)
    Optional<ConsentRecord> findOwnedBy(@Param("id") UUID id,
                                        @Param("grantedByUserId") UUID grantedByUserId);

    @Query("""
           select c from ConsentRecord c
           where c.grantedByUserId = :grantedByUserId
           order by c.createdAt desc
           """)
    List<ConsentRecord> findByGranter(@Param("grantedByUserId") UUID grantedByUserId);

    @Query("""
           select c from ConsentRecord c
           where c.grantedByUserId = :grantedByUserId and c.status = :status
           """)
    List<ConsentRecord> findByGranterAndStatus(@Param("grantedByUserId") UUID grantedByUserId,
                                               @Param("status") ConsentStatus status);
}
