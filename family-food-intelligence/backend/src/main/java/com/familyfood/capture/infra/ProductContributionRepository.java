package com.familyfood.capture.infra;

import com.familyfood.capture.domain.ProductContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the contribution queue. {@code save} is the intake path; the ops review
 * console that will otherwise query this table is a separate, later task.
 */
public interface ProductContributionRepository extends JpaRepository<ProductContribution, UUID> {

    /** A submitter's own contributions — used by account erasure and by data export. */
    List<ProductContribution> findBySubmittedByUserId(UUID submittedByUserId);
}
