package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCodeClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoCodeClaimRepository extends JpaRepository<PromoCodeClaim, Long> {
    long countByPromoCodeAcquisitionManagerIdAndConsumedAtIsNotNull(Long managerId);

    long countByPromoCodeIdAndConsumedAtIsNotNullAndSubscriptionIsNull(Long promoCodeId);

    @Query("select count(c) from PromoCodeClaim c, UserEntitlement e "
            + "where c.userId = e.userId "
            + "and c.promoCode.acquisitionManager.id = :managerId "
            + "and c.consumedAt is not null and c.subscription is null "
            + "and e.subscribed = true")
    long countActiveAppleSubscribersByManagerId(@Param("managerId") Long managerId);
}
