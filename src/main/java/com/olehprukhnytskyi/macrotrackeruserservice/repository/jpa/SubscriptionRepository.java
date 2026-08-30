package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByPurchaseTokenHash(String purchaseTokenHash);

    List<Subscription> findByUserIdOrderByExpiresAtDesc(Long userId);

    boolean existsByUserIdAndPromoCodeIsNotNull(Long userId);

    @Query("select count(distinct s.userId) from Subscription s "
            + "where s.promoCode.id = :promoCodeId")
    long countDistinctUsersByPromoCodeId(@Param("promoCodeId") Long promoCodeId);

    @Query("select count(distinct s.userId) from Subscription s "
            + "where s.promoCode.acquisitionManager.id = :managerId "
            + "and s.status in :statuses "
            + "and (s.expiresAt is null or s.expiresAt > CURRENT_TIMESTAMP)")
    long countActiveUsersByAcquisitionManagerId(
            @Param("managerId") Long managerId,
            @Param("statuses") List<SubscriptionStatus> statuses);
}
