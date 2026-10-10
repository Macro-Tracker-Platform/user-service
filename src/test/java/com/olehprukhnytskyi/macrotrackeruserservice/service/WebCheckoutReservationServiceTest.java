package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatCustomerSnapshot;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCheckoutRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebSubscriptionEligibilityDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebAffiliate;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class WebCheckoutReservationServiceTest {
    @Mock
    private UserRepository users;
    @Mock
    private WebCheckoutRepository checkouts;
    @Mock
    private PromoCodeRepository promos;
    @Mock
    private PromoCodeService promoService;
    @Mock
    private WebSubscriptionEligibilityService eligibility;
    @Mock
    private RevenueCatApiClient revenueCat;
    @Mock
    private StripeBillingClient stripe;
    private final WebBillingProperties properties = new WebBillingProperties();
    private WebCheckoutReservationService service;

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        properties.setStripeSecretKey("test");
        properties.setStripeWebhookSecret("test");
        properties.setMonthlyPriceId("price_month");
        properties.setYearlyPriceId("price_year");
        properties.setSuccessUrl("https://example.com/success");
        properties.setPortalReturnUrl("https://example.com/account");
        properties.setCancelUrl("https://example.com/cancel");
        RevenueCatProperties rc = new RevenueCatProperties();
        rc.setSubscriberApiKey("test");
        rc.setStripeApiKey("test");
        rc.setEnvironment("SANDBOX");
        service = new WebCheckoutReservationService(properties, rc, users, checkouts,
                promos, promoService, eligibility, revenueCat, stripe);
    }

    @Test
    void deletedOwnerCannotCreateStripeSessionAfterReservationCommit() {
        when(checkouts.findUserIdById("attempt")).thenReturn(Optional.of(42L));
        assertThatThrownBy(() -> service.createSession("attempt"))
                .hasMessageContaining("ACCOUNT_NOT_FOUND");
        verifyNoInteractions(stripe);
    }

    @Test
    void completedAttemptIsNotReopenedByAWaitingCreateRequest() {
        var completed = WebCheckout.builder().id("attempt").userId(42L)
                .state(WebCheckoutState.COMPLETED).build();
        when(checkouts.findUserIdById("attempt")).thenReturn(Optional.of(42L));
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        when(checkouts.findByIdForUpdate("attempt")).thenReturn(Optional.of(completed));
        assertThat(service.createSession("attempt")).isSameAs(completed);
        var order = org.mockito.Mockito.inOrder(users, checkouts);
        order.verify(users).findByIdForUpdate(42L);
        order.verify(checkouts).findByIdForUpdate("attempt");
        verifyNoInteractions(stripe);
    }

    @Test
    void disabledCheckoutMakesNoProviderCalls() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> service.prepare(42L, null, request(WebBillingPlan.YEARLY, null)))
                .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(revenueCat, stripe, checkouts);
    }

    @Test
    void activeSubscriptionCannotCreateCheckout() {
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        var snapshot = new RevenueCatCustomerSnapshot(
                true, true, Instant.now().plusSeconds(3600));
        when(revenueCat.customer(42L)).thenReturn(snapshot);
        when(eligibility.evaluateForCheckout(42L, null, snapshot)).thenReturn(
                new WebSubscriptionEligibilityDto(false, false, false, "EXISTING_ACCESS", null));
        assertThatThrownBy(() -> service.prepare(42L, null, request(WebBillingPlan.YEARLY, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("EXISTING_ACCESS");
        verifyNoInteractions(stripe);
    }

    @Test
    void creatingReservationIsReusedWithoutAnotherEligibilityOrPaymentRequest() {
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        WebCheckout checkout = WebCheckout.builder().id("attempt").userId(42L)
                .state(WebCheckoutState.CREATING).plan(WebBillingPlan.YEARLY)
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        when(checkouts.findFirstByUserIdAndStateInOrderByCreatedAtDesc(any(), any()))
                .thenReturn(Optional.of(checkout));
        assertThat(service.prepare(42L, null, request(WebBillingPlan.YEARLY, null)))
                .isSameAs(checkout);
        verifyNoInteractions(revenueCat, stripe);
    }

    @Test
    void affiliateAndReferrerRatesAreFrozenWithAnnualTrial() {
        eligible();
        WebAffiliate referrer = WebAffiliate.builder().id(2L).name("Referrer").active(true).build();
        WebAffiliate affiliate = WebAffiliate.builder().id(1L).name("Partner")
                .referrer(referrer).active(true).build();
        PromoCode promo = PromoCode.builder().id(3L).code("PARTNER").discountPercent(15)
                .acquisitionType(PromoAcquisitionType.AFFILIATE).webAffiliate(affiliate).build();
        when(promos.findByCodeIgnoreCase("PARTNER")).thenReturn(Optional.of(promo));
        when(promos.findByIdForUpdate(3L)).thenReturn(Optional.of(promo));
        WebCheckout result = service.prepare(
                42L, null, request(WebBillingPlan.YEARLY, " partner "));
        assertThat(result.getAffiliateId()).isEqualTo(1L);
        assertThat(result.getReferrerId()).isEqualTo(2L);
        assertThat(result.getCommissionPercent()).isEqualByComparingTo("35");
        assertThat(result.getReferrerCommissionPercent()).isEqualByComparingTo("15");
        assertThat(result.getTrialDays()).isEqualTo(7);
        assertThat(result.getDiscountPercent()).isEqualTo(15);
        assertThat(result.getState()).isEqualTo(WebCheckoutState.CREATING);
        org.mockito.Mockito.verify(stripe).validatePrice("price_year", WebBillingPlan.YEARLY);
    }

    @Test
    void monthlyAndReturningWebCustomersDoNotReceiveTrial() {
        eligible();
        assertThat(service.prepare(42L, null, request(WebBillingPlan.MONTHLY, null)).getTrialDays())
                .isZero();
        when(checkouts.existsByUserIdAndState(42L, WebCheckoutState.COMPLETED)).thenReturn(true);
        assertThat(service.prepare(42L, null, request(WebBillingPlan.YEARLY, null)).getTrialDays())
                .isZero();
    }

    private void eligible() {
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        var snapshot = new RevenueCatCustomerSnapshot(false, false, null);
        when(revenueCat.customer(42L)).thenReturn(snapshot);
        when(eligibility.evaluateForCheckout(42L, null, snapshot)).thenReturn(
                new WebSubscriptionEligibilityDto(true, true, false, "ELIGIBLE", null));
        when(checkouts.save(any(WebCheckout.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private WebCheckoutRequestDto request(WebBillingPlan plan, String code) {
        return new WebCheckoutRequestDto(plan, code);
    }
}
