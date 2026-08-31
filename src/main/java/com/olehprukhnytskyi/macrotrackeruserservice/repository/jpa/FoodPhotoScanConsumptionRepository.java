package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.FoodPhotoScanConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FoodPhotoScanConsumptionRepository
        extends JpaRepository<FoodPhotoScanConsumption, Long> {
    boolean existsByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
