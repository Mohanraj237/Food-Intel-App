package com.familyfood.identity.infra;

import com.familyfood.identity.domain.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("""
           select count(a) from LoginAttempt a
            where a.emailHash = :emailHash
              and a.succeeded = false
              and a.attemptedAt > :since
           """)
    long countRecentFailuresByEmail(@Param("emailHash") String emailHash,
                                    @Param("since") Instant since);

    @Query("""
           select count(a) from LoginAttempt a
            where a.ipHash = :ipHash
              and a.succeeded = false
              and a.attemptedAt > :since
           """)
    long countRecentFailuresByIp(@Param("ipHash") String ipHash,
                                 @Param("since") Instant since);

    @Modifying
    @Query("delete from LoginAttempt a where a.attemptedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
