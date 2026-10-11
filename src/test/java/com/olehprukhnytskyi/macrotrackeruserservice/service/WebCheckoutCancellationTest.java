package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WebCheckoutCancellationTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WebCheckoutRepository checkouts = mock(WebCheckoutRepository.class);
    private final StripeBillingClient stripe = mock(StripeBillingClient.class);
    private final WebCheckout checkout = WebCheckout.builder().id("attempt").userId(42L)
            .state(WebCheckoutState.OPEN).stripeSessionId("cs_owned")
            .checkoutUrl("https://checkout.stripe.com/c/pay/owned").build();
    private WebCheckoutReservationService service;

    @BeforeEach
    void setup() {
        service = new WebCheckoutReservationService(new WebBillingProperties(),
                new RevenueCatProperties(), users, checkouts, mock(PromoCodeRepository.class),
                mock(PromoCodeService.class), mock(WebSubscriptionEligibilityService.class),
                mock(RevenueCatApiClient.class), stripe);
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        when(checkouts.findByIdForUpdate("attempt")).thenReturn(Optional.of(checkout));
    }

    @Test
    void expiresOnlyOwnedOpenSessionEvenWhenNewSalesAreDisabled() throws Exception {
        when(stripe.session("cs_owned")).thenReturn(session("open"));
        when(stripe.expireSession("cs_owned")).thenReturn(session("expired"));
        assertThat(service.cancel(42L, "attempt").getState()).isEqualTo(WebCheckoutState.EXPIRED);
        assertThat(checkout.getCheckoutUrl()).isNull();
        var order = org.mockito.Mockito.inOrder(users, checkouts, stripe);
        order.verify(users).findByIdForUpdate(42L);
        order.verify(checkouts).findByIdForUpdate("attempt");
        order.verify(stripe).session("cs_owned");
        order.verify(stripe).expireSession("cs_owned");
    }

    @Test
    void cannotCancelAnotherUsersAttemptOrDeletedAccount() {
        checkout.setUserId(99L);
        assertThatThrownBy(() -> service.cancel(42L, "attempt"))
                .hasMessageContaining("CHECKOUT_NOT_FOUND");
        checkout.setUserId(42L);
        checkout.setAccountDeletedAt(Instant.now());
        assertThatThrownBy(() -> service.cancel(42L, "attempt"))
                .hasMessageContaining("CHECKOUT_NOT_FOUND");
        verifyNoInteractions(stripe);
    }

    @ParameterizedTest
    @EnumSource(value = WebCheckoutState.class, names = {"EXPIRED", "COMPLETED"})
    void repeatedCancellationOfTerminalAttemptNeverCancelsSubscription(WebCheckoutState state) {
        checkout.setState(state);
        assertThat(service.cancel(42L, "attempt").getState()).isEqualTo(state);
        verifyNoInteractions(stripe);
    }

    @Test
    void completionWinsRaceAndRequestsReceiptSyncInsteadOfNewCheckout() throws Exception {
        when(stripe.session("cs_owned")).thenReturn(session("open"), session("complete"));
        when(stripe.expireSession("cs_owned")).thenThrow(unavailable());
        assertThat(service.cancel(42L, "attempt").getState())
                .isEqualTo(WebCheckoutState.COMPLETED);
        assertThat(checkout.getStripeSubscriptionId()).isEqualTo("sub_owned");
        assertThat(checkout.getReceiptSyncRequestedAt()).isNotNull();
        verify(stripe, never()).cancelSubscription("sub_owned");
    }

    @Test
    void lostExpiryResponseIsReconciledFromStripe() throws Exception {
        when(stripe.session("cs_owned")).thenReturn(session("open"), session("expired"));
        when(stripe.expireSession("cs_owned")).thenThrow(unavailable());
        assertThat(service.cancel(42L, "attempt").getState()).isEqualTo(WebCheckoutState.EXPIRED);
    }

    @Test
    void providerFailureDoesNotReleaseStillOpenReservation() throws Exception {
        when(stripe.session("cs_owned")).thenReturn(session("open"));
        when(stripe.expireSession("cs_owned")).thenThrow(unavailable());
        assertThatThrownBy(() -> service.cancel(42L, "attempt"))
                .hasMessageContaining("CHECKOUT_CANCELLATION_UNCONFIRMED");
        assertThat(checkout.getState()).isEqualTo(WebCheckoutState.OPEN);
    }

    @Test
    void unknownCreatingAttemptCannotBeDiscardedWithoutProviderEvidence() {
        checkout.setState(WebCheckoutState.CREATING);
        checkout.setStripeSessionId(null);
        when(stripe.findSession(checkout)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.cancel(42L, "attempt"))
                .hasMessageContaining("CHECKOUT_RECONCILIATION_REQUIRED");
        assertThat(checkout.getState()).isEqualTo(WebCheckoutState.CREATING);
        verify(stripe, never()).createCheckout(checkout);
    }

    @Test
    void recoveredCreatingSessionCanBeClosedWithoutCreatingAnother() throws Exception {
        checkout.setState(WebCheckoutState.CREATING);
        checkout.setStripeSessionId(null);
        when(stripe.findSession(checkout)).thenReturn(Optional.of(session("open")));
        when(stripe.expireSession("cs_owned")).thenReturn(session("expired"));
        assertThat(service.cancel(42L, "attempt").getState()).isEqualTo(WebCheckoutState.EXPIRED);
        verify(stripe, never()).createCheckout(checkout);
    }

    @Test
    void mismatchedProviderSessionCannotBeExpired() throws Exception {
        when(stripe.session("cs_owned")).thenReturn(new ObjectMapper().readTree(
                session("open").toString().replace("attempt", "someone-else")));
        assertThatThrownBy(() -> service.cancel(42L, "attempt"))
                .hasMessageContaining("CHECKOUT_PROVIDER_MISMATCH");
        verify(stripe, never()).expireSession("cs_owned");
    }

    @Test
    void staleStatusResponseCannotReopenCanceledCheckout() throws Exception {
        checkout.setState(WebCheckoutState.EXPIRED);
        checkout.setCheckoutUrl(null);
        service.applySession(checkout, session("open"));
        assertThat(checkout.getState()).isEqualTo(WebCheckoutState.EXPIRED);
        assertThat(checkout.getCheckoutUrl()).isNull();
    }

    private JsonNode session(String status) throws Exception {
        return new ObjectMapper().readTree("""
                {"id":"cs_owned","livemode":false,"metadata":{"checkout_id":"attempt"},
                 "client_reference_id":"42","mode":"subscription","status":"%s",
                 "url":"https://checkout.stripe.com/c/pay/owned","subscription":"sub_owned"}
                """.formatted(status));
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_PROVIDER_UNAVAILABLE");
    }
}
