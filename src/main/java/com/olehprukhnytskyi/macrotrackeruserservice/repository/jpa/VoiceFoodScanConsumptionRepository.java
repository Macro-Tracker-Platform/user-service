package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.VoiceFoodScanConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoiceFoodScanConsumptionRepository
        extends JpaRepository<VoiceFoodScanConsumption, Long> {
    boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
