package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatCustomerSnapshot;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebPriceDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebSubscriptionEligibilityDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import com.olehprukhnytskyi.util.UserRole;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WebStorefrontServiceTest {
    private final WebBillingProperties properties = new WebBillingProperties();
    private final WebCheckoutReservationService reservations = mock(
            WebCheckoutReservationService.class);
    private final WebSubscriptionEligibilityService eligibility = mock(
            WebSubscriptionEligibilityService.class);
    private final RevenueCatApiClient revenueCat = mock(RevenueCatApiClient.class);
    private final StripeBillingClient stripe = mock(StripeBillingClient.class);
    private final WebCheckoutRepository checkouts = mock(WebCheckoutRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WebStorefrontService service = new WebStorefrontService(properties,
            reservations, eligibility, revenueCat, stripe, checkouts,
            mock(SubscriptionService.class),
            users);

    @Test
    void campaignAdminCapabilityUsesStoredRoleRatherThanBrowserOrStaleClaim() {
        var user = new User();
        user.setEmail("owner@example.com");
        when(users.findById(42L)).thenReturn(Optional.of(user));
        assertThat(service.account(42L, "ADMIN").campaignAdmin()).isFalse();
        user.addRole(UserRole.ADMIN);
        assertThat(service.account(42L, "USER").campaignAdmin()).isTrue();
    }

    @Test
    void disabledBillingDoesNotInventPricesOrCallProviders() {
        var result = service.catalog(42L, "USER");
        assertThat(result.available()).isFalse();
        assertThat(result.prices()).isEmpty();
        verifyNoInteractions(stripe, revenueCat, eligibility, checkouts);
    }

    @Test
    void catalogUsesFreshHistoryAndDoesNotOfferAnotherWebTrial() {
        properties.setEnabled(true);
        properties.setMonthlyPriceId("price_month");
        properties.setYearlyPriceId("price_year");
        var snapshot = new RevenueCatCustomerSnapshot(false, false, null);
        when(revenueCat.customer(42L)).thenReturn(snapshot);
        when(eligibility.evaluateForCheckout(42L, "USER", snapshot)).thenReturn(
                new WebSubscriptionEligibilityDto(true, true, false, "ELIGIBLE", null));
        when(checkouts.existsByUserIdAndState(42L, WebCheckoutState.COMPLETED)).thenReturn(true);
        when(stripe.price("price_month", WebBillingPlan.MONTHLY)).thenReturn(
                new WebPriceDto("MONTHLY", 1299, "usd", 2));
        when(stripe.price("price_year", WebBillingPlan.YEARLY)).thenReturn(
                new WebPriceDto("YEARLY", 6999, "usd", 2));
        var result = service.catalog(42L, "USER");
        assertThat(result.available()).isTrue();
        assertThat(result.prices()).extracting(WebPriceDto::unitAmount)
                .containsExactly(1299L, 6999L);
        assertThat(result.eligibility().trialEligible()).isFalse();
        verify(reservations).requireConfigured();
    }

    @Test
    void portalDerivesCustomerFromOwnedSubscriptionNotBrowser() throws Exception {
        properties.setPortalReturnUrl("https://macrotracker.uk/account.html");
        ownedCheckout();
        when(stripe.subscription("sub_owned")).thenReturn(new ObjectMapper().readTree("""
                {"id":"sub_owned","customer":"cus_owned","livemode":false,
                "metadata":{"checkout_id":"owned"}}
                """));
        when(stripe.portal("cus_owned", properties.getPortalReturnUrl())).thenReturn(
                new ObjectMapper().readTree("{\"url\":\"https://billing.stripe.com/p/session/owned\"}"));
        assertThat(service.portal(42L)).containsEntry("url",
                "https://billing.stripe.com/p/session/owned");
    }

    @Test
    void portalRejectsMismatchedOwnership() throws Exception {
        ownedCheckout();
        when(stripe.subscription("sub_owned")).thenReturn(new ObjectMapper().readTree("""
                {"id":"sub_owned","customer":"cus_other","livemode":false,
                "metadata":{"checkout_id":"other"}}
                """));
        assertThatThrownBy(() -> service.portal(42L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("MISMATCH");
    }

    @Test
    void storeCustomerWithoutOwnedWebCheckoutCannotOpenPortal() {
        properties.setStripeSecretKey("test_secret");
        assertThatThrownBy(() -> service.portal(42L))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("NOT_FOUND");
        verifyNoInteractions(stripe);
    }

    @Test
    void mobilePortalUsesSeparateCancellationOnlyConfigurationEvenWhenSalesDisabled()
            throws Exception {
        ownedMobilePortal();
        when(stripe.portalConfiguration("bpc_mobile")).thenReturn(mobileConfiguration());
        when(stripe.mobilePortal("cus_owned", "bpc_mobile",
                properties.getMobilePortalReturnUrl())).thenReturn(new ObjectMapper()
                    .readTree("{\"url\":\"https://billing.stripe.com/p/session/mobile\"}"));
        assertThat(service.mobilePortal(42L)).containsEntry("url",
                "https://billing.stripe.com/p/session/mobile");
        verify(stripe, never()).portal(anyString(), anyString());
    }

    @Test
    void mobilePortalRefusesUpsellsRetentionWrongEnvironmentAndMissingConfiguration()
            throws Exception {
        ownedMobilePortal();
        for (String feature : List.of("subscription_update", "payment_method_update",
                "customer_update", "subscription_pause")) {
            var config = mobileConfiguration();
            ((com.fasterxml.jackson.databind.node.ObjectNode) config.path("features"))
                    .putObject(feature).put("enabled", true);
            when(stripe.portalConfiguration("bpc_mobile")).thenReturn(config);
            assertThatThrownBy(() -> service.mobilePortal(42L))
                    .hasMessageContaining("UNSAFE_CONFIGURATION");
        }
        var retained = mobileConfiguration();
        ((com.fasterxml.jackson.databind.node.ObjectNode) retained.path("features")
                .path("subscription_cancel")).putObject("retention").put("type", "coupon_offer");
        when(stripe.portalConfiguration("bpc_mobile")).thenReturn(retained);
        assertThatThrownBy(() -> service.mobilePortal(42L))
                .hasMessageContaining("UNSAFE_CONFIGURATION");
        var wrongMode = mobileConfiguration();
        wrongMode.put("livemode", true);
        when(stripe.portalConfiguration("bpc_mobile")).thenReturn(wrongMode);
        assertThatThrownBy(() -> service.mobilePortal(42L))
                .hasMessageContaining("UNSAFE_CONFIGURATION");
        properties.setMobilePortalConfigurationId(null);
        assertThatThrownBy(() -> service.mobilePortal(42L))
                .hasMessageContaining("NOT_CONFIGURED");
        verify(stripe, never()).mobilePortal(anyString(), anyString(), anyString());
    }

    @Test
    void mobilePortalCannotReturnToTheWebsitePurchaseAccount() throws Exception {
        ownedMobilePortal();
        properties.setMobilePortalReturnUrl("https://macrotracker.uk/account.html");
        assertThatThrownBy(() -> service.mobilePortal(42L))
                .hasMessageContaining("BILLING_PORTAL_NOT_CONFIGURED");
        verify(stripe, never()).portalConfiguration(anyString());
        verify(stripe, never()).mobilePortal(anyString(), anyString(), anyString());
    }

    private void ownedMobilePortal() throws Exception {
        ownedCheckout();
        properties.setMobilePortalConfigurationId("bpc_mobile");
        properties.setMobilePortalReturnUrl("https://macrotracker.uk/subscription-managed.html");
        when(stripe.subscription("sub_owned")).thenReturn(new ObjectMapper().readTree("""
                {"id":"sub_owned","customer":"cus_owned","livemode":false,
                "metadata":{"checkout_id":"owned"}}
                """));
    }

    private com.fasterxml.jackson.databind.node.ObjectNode mobileConfiguration() throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode) new ObjectMapper().readTree("""
                {"id":"bpc_mobile","active":true,"livemode":false,"features":{
                  "subscription_cancel":{"enabled":true,"mode":"at_period_end",
                    "proration_behavior":"none","retention":null},
                  "subscription_update":{"enabled":false},
                  "payment_method_update":{"enabled":false},
                  "customer_update":{"enabled":false}}}
                """);
    }

    private void ownedCheckout() {
        properties.setStripeSecretKey("test_secret");
        when(checkouts.findFirstByUserIdAndStateInOrderByCreatedAtDesc(42L,
                List.of(WebCheckoutState.COMPLETED))).thenReturn(Optional.of(WebCheckout.builder()
                    .id("owned").userId(42L).stripeSubscriptionId("sub_owned").build()));
    }
}
