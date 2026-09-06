package com.familyfood.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

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
