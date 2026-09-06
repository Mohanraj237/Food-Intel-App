package com.familyfood.capture.infra;

import com.familyfood.capture.domain.ContributionStatus;
import com.familyfood.capture.domain.ProductContribution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Persistence for the contribution queue. {@code save} is the intake path;
 * {@link #findByStatusOrderBySubmittedAtAsc} backs the ops-review queue (oldest first, so
 * nothing sits unreviewed indefinitely).
 */
public interface ProductContributionRepository extends JpaRepository<ProductContribution, UUID> {

    /** A submitter's own contributions — used by account erasure and by data export. */
    List<ProductContribution> findBySubmittedByUserId(UUID submittedByUserId);

    /** The ops-review queue for one status, oldest submission first. */
    List<ProductContribution> findByStatusOrderBySubmittedAtAsc(ContributionStatus status);
}
