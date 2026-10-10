package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatCustomerSnapshot;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebSubscriptionEligibilityDto;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.RevenueCatSubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserEntitlementRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebSubscriptionEligibilityService {
    private final SubscriptionService subscriptionService;
    private final TrialEligibilityService trialEligibilityService;
    private final UserEntitlementRepository entitlementRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final RevenueCatSubscriptionRepository revenueCatSubscriptionRepository;

    @Transactional(readOnly = true)
    public WebSubscriptionEligibilityDto evaluateForCheckout(
            Long userId, String roles, RevenueCatCustomerSnapshot customer) {
        boolean active = customer.active() || subscriptionService.hasLifetimeProRole(userId, roles)
                || revenueCatSubscriptionRepository.findByUserId(userId).stream()
                    .anyMatch(item -> item.isActive()
                            && item.getExpiresAt().isAfter(java.time.Instant.now()))
                || subscriptionRepository.findByUserIdOrderByExpiresAtDesc(userId).stream()
                    .anyMatch(item -> item.getExpiresAt() != null
                            && item.getExpiresAt().isAfter(java.time.Instant.now())
                            && (item.getStatus() == SubscriptionStatus.PRO_ACTIVE
                                || item.getStatus() == SubscriptionStatus.PRO_GRACE_PERIOD
                                || item.getStatus() == SubscriptionStatus.PRO_CANCELED_BUT_ACTIVE));
        boolean pending = subscriptionRepository.findByUserIdOrderByExpiresAtDesc(userId).stream()
                .anyMatch(item -> item.getStatus() == SubscriptionStatus.PENDING);
        return new WebSubscriptionEligibilityDto(!active && !pending,
                !active && !pending && !customer.hasPurchaseHistory()
                    && trialEligibilityService.isEligible(userId), false,
                active ? "EXISTING_ACCESS" : pending ? "PENDING_STORE_PURCHASE" : "ELIGIBLE",
                customer.expiresAt());
    }

    @Transactional(readOnly = true)
    public WebSubscriptionEligibilityDto evaluate(Long userId, String roles) {
        EntitlementResponseDto entitlement = subscriptionService.getEntitlement(userId, roles);
        boolean incompleteHistory = entitlementRepository.findById(userId)
                .map(value -> !revenueCatSubscriptionRepository.existsByUserId(userId))
                .orElse(false);
        if (incompleteHistory) {
            return new WebSubscriptionEligibilityDto(false, false, true,
                    "SUBSCRIPTION_RECONCILIATION_REQUIRED", entitlement.getValidUntil());
        }
        if (entitlement.getState() == SubscriptionStatus.PENDING
                || subscriptionRepository.findByUserIdOrderByExpiresAtDesc(userId).stream()
                    .anyMatch(item -> item.getStatus() == SubscriptionStatus.PENDING)) {
            return new WebSubscriptionEligibilityDto(false, false, false,
                    "PENDING_STORE_PURCHASE", entitlement.getValidUntil());
        }
        boolean active = "PRO".equals(entitlement.getPlan());
        return new WebSubscriptionEligibilityDto(!active,
                !active && trialEligibilityService.isEligible(userId), false,
                active ? "EXISTING_ACCESS" : "ELIGIBLE", entitlement.getValidUntil());
    }
}
