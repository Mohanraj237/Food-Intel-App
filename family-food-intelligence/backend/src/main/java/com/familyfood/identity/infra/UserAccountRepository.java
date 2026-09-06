package com.familyfood.identity.infra;

import com.familyfood.identity.domain.UserAccount;
import com.familyfood.identity.domain.UserAccount.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    Optional<UserAccount> findByEmail(String email);

    boolean existsByEmail(String email);

    /** Accounts past their erasure grace period, for the scheduled hard-delete job. */
    List<UserAccount> findByDeletedAtBefore(Instant cutoff);

    /**
     * The scheduled hard-erasure sweep's own query: accounts still {@code
     * DELETION_REQUESTED} (not already {@code ERASED}) whose grace period has elapsed.
     */
    List<UserAccount> findByStatusAndDeletedAtBefore(AccountStatus status, Instant cutoff);
}
