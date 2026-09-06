package com.familyfood.activity.infra;

import com.familyfood.activity.domain.ActivityEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, UUID> {

    /** Newest-first, scoped to one account only — never another account's events. */
    List<ActivityEvent> findByAccountIdOrderByOccurredAtDesc(UUID accountId, Pageable pageable);
}
