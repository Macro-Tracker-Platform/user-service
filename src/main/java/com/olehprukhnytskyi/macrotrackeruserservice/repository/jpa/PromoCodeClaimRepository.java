package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCodeClaim;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoCodeClaimRepository extends JpaRepository<PromoCodeClaim, Long> {
    long countByPromoCodeAcquisitionManagerIdAndConsumedAtIsNotNull(Long managerId);
}
