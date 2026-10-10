package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebCheckoutRepository extends JpaRepository<WebCheckout, String> {
    Optional<WebCheckout> findFirstByUserIdAndStateInOrderByCreatedAtDesc(
            Long userId, Collection<WebCheckoutState> states);

    @Query("select c.userId from WebCheckout c where c.id = :id")
    Optional<Long> findUserIdById(@Param("id") String id);

    List<WebCheckout> findByUserIdOrderByIdAsc(Long userId);

    boolean existsByUserIdAndAccountDeletedAtIsNotNull(Long userId);

    long countByPromoCodeIdAndStateIn(Long promoCodeId, Collection<WebCheckoutState> states);

    boolean existsByUserIdAndStateAndPromoCodeIdIsNotNull(Long userId, WebCheckoutState state);

    boolean existsByUserIdAndState(Long userId, WebCheckoutState state);

    @Query("select c from WebCheckout c where c.state = :state "
            + "and c.stripeSubscriptionId is not null and c.receiptSyncedAt is null "
            + "and c.accountDeletedAt is null "
            + "order by c.receiptSyncRequestedAt asc")
    List<WebCheckout> pendingReceiptSync(@Param("state") WebCheckoutState state,
                                        org.springframework.data.domain.Pageable page);

    @Transactional
    @Modifying
    @Query("update WebCheckout c set c.receiptSyncRequestedAt = :retryAt "
            + "where c.id = :id and c.receiptSyncRequestedAt = :requestedAt "
            + "and c.receiptSyncedAt is null")
    void deferReceiptSync(@Param("id") String id, @Param("requestedAt") Instant requestedAt,
                          @Param("retryAt") Instant retryAt);

    Optional<WebCheckout> findByIdAndUserId(String id, Long userId);

    @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WebCheckout c where c.id = :id")
    Optional<WebCheckout> findByIdForUpdate(@Param("id") String id);

    @Transactional
    @Modifying
    @Query("update WebCheckout c set c.receiptSyncedAt = :syncedAt "
            + "where c.id = :id and c.stripeSubscriptionId = :subscriptionId "
            + "and c.receiptSyncRequestedAt = :requestedAt")
    void markReceiptSynced(@Param("id") String id,
                           @Param("subscriptionId") String subscriptionId,
                           @Param("syncedAt") Instant syncedAt,
                           @Param("requestedAt") Instant requestedAt);
}
