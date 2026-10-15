package com.familyfood.scoring.infra;

import com.familyfood.scoring.domain.RescoreRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RescoreRunRepository extends JpaRepository<RescoreRun, UUID> {
}
