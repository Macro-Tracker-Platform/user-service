package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WebAccountDeletionServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final WebCheckoutRepository checkouts = mock(WebCheckoutRepository.class);
    private final StripeBillingClient stripe = mock(StripeBillingClient.class);
    private final WebBillingProperties properties = new WebBillingProperties();
    private final WebCheckout checkout = WebCheckout.builder().id("owned").userId(42L)
            .state(WebCheckoutState.COMPLETED).stripeSubscriptionId("sub_owned").build();
    private final WebCheckoutReservationService reservations =
            new WebCheckoutReservationService(properties, new RevenueCatProperties(),
                    null, checkouts, null, null, null, null, stripe);
    private final WebAccountDeletionService service =
            new WebAccountDeletionService(checkouts, stripe, properties, reservations);

    @BeforeEach
    void setup() {
        when(checkouts.findByUserIdOrderByIdAsc(42L)).thenReturn(List.of(checkout));
        when(checkouts.findByIdForUpdate("owned")).thenReturn(Optional.of(checkout));
    }

    @Test
    void cancelsOwnedTrialEvenWithNewSalesDisabled() throws Exception {
        when(stripe.subscription("sub_owned")).thenReturn(subscription("trialing"));
        when(stripe.cancelSubscription("sub_owned")).thenReturn(subscription("canceled"));
        service.prepare(42L);
        verify(stripe).cancelSubscription("sub_owned");
    }

    @Test
    void retryDoesNotCancelAnAlreadyCanceledSubscription() throws Exception {
        when(stripe.subscription("sub_owned")).thenReturn(subscription("canceled"));
        service.prepare(42L);
        verify(stripe, never()).cancelSubscription("sub_owned");
    }

    @Test
    void providerFailurePreventsAccountDeletionFromProceeding() throws Exception {
        when(stripe.subscription("sub_owned")).thenReturn(subscription("active"));
        when(stripe.cancelSubscription("sub_owned")).thenThrow(unavailable());
        assertThatThrownBy(() -> service.prepare(42L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void wrongOwnerOrEnvironmentNeverCancelsAnotherSubscription() throws Exception {
        for (String field : List.of("owner", "environment", "id")) {
            var sub = (com.fasterxml.jackson.databind.node.ObjectNode) subscription("active");
            if ("owner".equals(field)) {
                ((com.fasterxml.jackson.databind.node.ObjectNode) sub.get("metadata"))
                        .put("checkout_id", "someone-else");
            } else if ("environment".equals(field)) {
                sub.put("livemode", true);
            } else {
                sub.put("id", "sub_other");
            }
            when(stripe.subscription("sub_owned")).thenReturn(sub);
            assertThatThrownBy(() -> service.prepare(42L))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("BILLING_CUSTOMER_MISMATCH");
        }
        verify(stripe, never()).cancelSubscription("sub_owned");
    }

    @Test
    void expiresUnpaidOpenCheckoutBeforeDeletion() throws Exception {
        pending(WebCheckoutState.OPEN);
        when(stripe.session("cs_owned")).thenReturn(session("open"));
        when(stripe.expireSession("cs_owned")).thenReturn(session("expired"));
        service.prepare(42L);
        assertThat(checkout.getState()).isEqualTo(WebCheckoutState.EXPIRED);
        verify(stripe, never()).cancelSubscription("sub_owned");
    }

    @Test
    void completionWinningExpiryRaceIsCanceledBeforeDeletion() throws Exception {
        pending(WebCheckoutState.OPEN);
        when(stripe.session("cs_owned")).thenReturn(session("open"), session("complete"));
        when(stripe.expireSession("cs_owned")).thenThrow(unavailable());
        when(stripe.subscription("sub_owned")).thenReturn(subscription("trialing"));
        when(stripe.cancelSubscription("sub_owned")).thenReturn(subscription("canceled"));
        service.prepare(42L);
        verify(stripe).cancelSubscription("sub_owned");
    }

    @Test
    void uncertainCreatingAttemptRequiresReconciliationNotLocalTtl() {
        pending(WebCheckoutState.CREATING);
        checkout.setStripeSessionId(null);
        when(stripe.findSession(checkout)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.prepare(42L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("CHECKOUT_RECONCILIATION_REQUIRED");
    }

    @Test
    void canceledResponseMustBeConfirmed() throws Exception {
        when(stripe.subscription("sub_owned")).thenReturn(subscription("active"));
        when(stripe.cancelSubscription("sub_owned")).thenReturn(subscription("active"));
        assertThatThrownBy(() -> service.prepare(42L))
                .hasMessageContaining("WEB_SUBSCRIPTION_CANCELLATION_UNCONFIRMED");
    }

    private void pending(WebCheckoutState state) {
        checkout.setState(state);
        checkout.setStripeSessionId("cs_owned");
        checkout.setStripeSubscriptionId(null);
    }

    private JsonNode subscription(String status) throws Exception {
        return mapper.readTree("""
                {"id":"sub_owned","livemode":false,"status":"%s",
                 "metadata":{"checkout_id":"owned"}}
                """.formatted(status));
    }

    private JsonNode session(String status) throws Exception {
        return mapper.readTree("""
                {"id":"cs_owned","livemode":false,"status":"%s","mode":"subscription",
                 "client_reference_id":"42","metadata":{"checkout_id":"owned"},
                 "subscription":"sub_owned","url":"https://checkout.stripe.com/test"}
                """.formatted(status));
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
