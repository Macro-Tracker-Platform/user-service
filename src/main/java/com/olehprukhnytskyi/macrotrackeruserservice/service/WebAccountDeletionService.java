package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebAccountDeletionService {
    private final WebCheckoutRepository checkouts;
    private final StripeBillingClient stripe;
    private final WebBillingProperties properties;
    private final WebCheckoutReservationService reservations;

    // The caller holds the same user lock used by checkout creation and receipt imports.
    @Transactional(propagation = Propagation.MANDATORY)
    public void prepare(Long userId) {
        for (WebCheckout found : checkouts.findByUserIdOrderByIdAsc(userId)) {
            WebCheckout checkout = checkouts.findByIdForUpdate(found.getId()).orElseThrow();
            closeSession(checkout);
            if (checkout.getStripeSubscriptionId() != null) {
                closeSubscription(checkout);
            }
        }
    }

    private void closeSession(WebCheckout checkout) {
        if (checkout.getState() != WebCheckoutState.CREATING
                && checkout.getState() != WebCheckoutState.OPEN) {
            return;
        }
        JsonNode session = checkout.getStripeSessionId() == null
                ? stripe.findSession(checkout).orElseThrow(() ->
                    conflict("CHECKOUT_RECONCILIATION_REQUIRED"))
                : stripe.session(checkout.getStripeSessionId());
        reservations.applySession(checkout, session);
        if (checkout.getState() == WebCheckoutState.OPEN) {
            try {
                session = stripe.expireSession(checkout.getStripeSessionId());
            } catch (ResponseStatusException exception) {
                // Completion can win the race with expiry. Read again before deciding.
                session = stripe.session(checkout.getStripeSessionId());
            }
            reservations.applySession(checkout, session);
            if (checkout.getState() == WebCheckoutState.OPEN) {
                throw conflict("CHECKOUT_CANCELLATION_UNCONFIRMED");
            }
        }
    }

    private void closeSubscription(WebCheckout checkout) {
        JsonNode subscription = stripe.subscription(checkout.getStripeSubscriptionId());
        requireOwned(checkout, subscription);
        String status = subscription.path("status").asText();
        if (Set.of("canceled", "incomplete_expired").contains(status)) {
            return;
        }
        if (!Set.of("active", "trialing", "past_due", "unpaid", "incomplete", "paused")
                .contains(status)) {
            throw conflict("WEB_SUBSCRIPTION_CANCELLATION_UNCONFIRMED");
        }
        subscription = stripe.cancelSubscription(checkout.getStripeSubscriptionId());
        requireOwned(checkout, subscription);
        if (!"canceled".equals(subscription.path("status").asText())) {
            throw conflict("WEB_SUBSCRIPTION_CANCELLATION_UNCONFIRMED");
        }
    }

    private void requireOwned(WebCheckout checkout, JsonNode subscription) {
        if (subscription == null || !subscription.path("livemode").isBoolean()
                || subscription.path("livemode").asBoolean() != properties.isLiveMode()
                || !checkout.getStripeSubscriptionId().equals(subscription.path("id").asText())
                || !checkout.getId().equals(subscription.path("metadata")
                    .path("checkout_id").asText())) {
            throw conflict("BILLING_CUSTOMER_MISMATCH");
        }
    }

    private ResponseStatusException conflict(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
