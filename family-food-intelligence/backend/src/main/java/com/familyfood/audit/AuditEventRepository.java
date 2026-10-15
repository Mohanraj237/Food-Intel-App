package com.familyfood.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    /**
     * Events that touched one person's data, by anyone other than {@code excludeActor} — the
     * access log's query (D-044). Uses {@code ix_audit_subject}. An event with no actor (a
     * system process) is included: "the system read your data" is still worth being able to see.
     */
    @Query("""
           select e from AuditEvent e
           where e.subjectMemberId = :subjectMemberId
             and (e.actorUserId is null or e.actorUserId <> :excludeActor)
             and e.occurredAt >= :since
           order by e.occurredAt desc
           """)
    List<AuditEvent> findBySubjectExcludingActor(@Param("subjectMemberId") UUID subjectMemberId,
                                                 @Param("excludeActor") UUID excludeActor,
                                                 @Param("since") Instant since,
                                                 Pageable page);

    @Query("""
           select e from AuditEvent e
           where e.householdId = :householdId
           order by e.occurredAt desc
           """)
    List<AuditEvent> findByHousehold(@Param("householdId") UUID householdId);

    @Query("""
           select e from AuditEvent e
           where e.resourceType = :resourceType and e.resourceId = :resourceId
           order by e.occurredAt desc
           """)
    List<AuditEvent> findByResource(@Param("resourceType") String resourceType,
                                    @Param("resourceId") UUID resourceId);
}
