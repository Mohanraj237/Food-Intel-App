package com.familyfood.consent.infra;

import com.familyfood.consent.domain.ConsentPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ConsentPurposeRepository extends JpaRepository<ConsentPurpose, String> {

    @Query("select p from ConsentPurpose p where p.active = true order by p.sortOrder")
    List<ConsentPurpose> findActiveOrdered();

    @Query("select p from ConsentPurpose p where p.code = :code and p.active = true")
    Optional<ConsentPurpose> findActiveByCode(String code);
}
