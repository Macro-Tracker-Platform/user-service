package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserEntitlement;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.RevenueCatSubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserEntitlementRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WebSubscriptionEligibilityServiceTest {
    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private TrialEligibilityService trialEligibilityService;
    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private RevenueCatSubscriptionRepository revenueCatSubscriptionRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @InjectMocks
    private WebSubscriptionEligibilityService service;

    @Test
    void activeSubscriberCannotStartSecondSubscription() {
        entitlement("PRO", SubscriptionStatus.PRO_CANCELED_BUT_ACTIVE);

        var result = service.evaluate(42L, null);

        assertThat(result.checkoutEligible()).isFalse();
        assertThat(result.trialEligible()).isFalse();
        assertThat(result.reason()).isEqualTo("EXISTING_ACCESS");
    }

    @Test
    void returningSubscriberCanGetDiscountWithoutAnotherTrial() {
        entitlement("FREE", SubscriptionStatus.PRO_EXPIRED);

        var result = service.evaluate(42L, null);

        assertThat(result.checkoutEligible()).isTrue();
        assertThat(result.trialEligible()).isFalse();
    }

    @Test
    void newAccountCanGetTrialAccordingToKnownHistory() {
        entitlement("FREE", SubscriptionStatus.FREE);
        when(trialEligibilityService.isEligible(42L)).thenReturn(true);

        assertThat(service.evaluate(42L, null).trialEligible()).isTrue();
    }

    @Test
    void booleanOnlyHistoryMustBeReconciledEvenIfInactive() {
        entitlement("FREE", SubscriptionStatus.FREE);
        when(entitlementRepository.findById(42L)).thenReturn(Optional.of(
                UserEntitlement.builder().userId(42L).subscribed(false).build()));

        var result = service.evaluate(42L, null);

        assertThat(result.checkoutEligible()).isFalse();
        assertThat(result.reconciliationRequired()).isTrue();
    }

    @Test
    void pendingStorePaymentBlocksWebCheckout() {
        entitlement("FREE", SubscriptionStatus.PENDING);

        assertThat(service.evaluate(42L, null).reason()).isEqualTo("PENDING_STORE_PURCHASE");
    }

    @Test
    void pendingPurchaseIsDetectedAlongsideAnExpiredRecord() {
        entitlement("FREE", SubscriptionStatus.PRO_EXPIRED);
        when(subscriptionRepository.findByUserIdOrderByExpiresAtDesc(42L))
                .thenReturn(List.of(Subscription.builder()
                        .status(SubscriptionStatus.PENDING).build()));

        assertThat(service.evaluate(42L, null).checkoutEligible()).isFalse();
    }

    @Test
    void freshRevenueCatAccessBlocksCheckoutWithoutLocalWebhookHistory() {
        var snapshot = new com.olehprukhnytskyi.macrotrackeruserservice.dto
                .RevenueCatCustomerSnapshot(true, true, java.time.Instant.now().plusSeconds(3600));
        assertThat(service.evaluateForCheckout(42L, null, snapshot).checkoutEligible()).isFalse();
    }

    @Test
    void freshExpiredHistoryAllowsDiscountButNeverAnotherTrial() {
        var snapshot = new com.olehprukhnytskyi.macrotrackeruserservice.dto
                .RevenueCatCustomerSnapshot(false, true, null);
        var result = service.evaluateForCheckout(42L, null, snapshot);
        assertThat(result.checkoutEligible()).isTrue();
        assertThat(result.trialEligible()).isFalse();
    }

    private void entitlement(String plan, SubscriptionStatus state) {
        when(subscriptionService.getEntitlement(42L, null)).thenReturn(
                EntitlementResponseDto.builder().plan(plan).state(state).build());
    }
}
