package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebRevenueCatAccessService {
    private final WebBillingProperties properties;
    private final WebCheckoutRepository checkoutRepository;
    private final RevenueCatApiClient revenueCat;

    public Subscription currentAccess(Long userId) {
        if (!properties.isEnabled() || !checkoutRepository.existsByUserIdAndState(
                userId, WebCheckoutState.COMPLETED)) {
            return null;
        }
        // Stripe renewals can arrive later than the cached period's expiration.
        var snapshot = revenueCat.customer(userId);
        if (!snapshot.active()) {
            return null;
        }
        if (snapshot.expiresAt() == null || !snapshot.expiresAt().isAfter(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "WEB_ENTITLEMENT_RECONCILIATION_REQUIRED");
        }
        return Subscription.builder().userId(userId).status(SubscriptionStatus.PRO_ACTIVE)
                .provider(snapshot.store()).expiresAt(snapshot.expiresAt()).build();
    }
}
