package com.familyfood.scoring.infra;

import com.familyfood.scoring.domain.ReferenceStandard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceStandardRepository extends JpaRepository<ReferenceStandard, String> {
}
