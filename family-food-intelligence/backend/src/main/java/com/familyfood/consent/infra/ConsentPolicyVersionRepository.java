package com.familyfood.consent.infra;

import com.familyfood.consent.domain.ConsentPolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConsentPolicyVersionRepository extends JpaRepository<ConsentPolicyVersion, UUID> {

    Optional<ConsentPolicyVersion> findByPurposeCodeAndVersion(String purposeCode, String version);

    /** The version currently on offer for a purpose — the one an open policy row names. */
    @Query("""
           select v from ConsentPolicyVersion v
           where v.purposeCode = :purposeCode and v.effectiveTo is null
           order by v.effectiveFrom desc
           """)
    java.util.List<ConsentPolicyVersion> findCurrent(@Param("purposeCode") String purposeCode);
}
