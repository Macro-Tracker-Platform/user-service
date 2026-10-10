package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebBillingEvent;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPayment;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPaymentRefund;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebBillingEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRefundRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRepository;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class StripeWebhookService {
    private final ObjectMapper objectMapper;
    private final StripeSignatureVerifier signatureVerifier;
    private final StripeBillingClient stripe;
    private final WebBillingProperties properties;
    private final WebCheckoutRepository checkoutRepository;
    private final WebBillingEventRepository eventRepository;
    private final WebPaymentRepository paymentRepository;
    private final WebPaymentRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final WebCheckoutReservationService reservations;
    private final WebCommissionService commissions;

    @Transactional
    public void handle(byte[] payload, String signature) {
        signatureVerifier.verify(payload, signature);
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (IOException exception) {
            throw invalid();
        }
        if (event == null || !event.isObject()) {
            throw invalid();
        }
        String eventId = event.path("id").asText();
        if (!eventId.startsWith("evt_") || !event.path("livemode").isBoolean()) {
            throw invalid();
        }
        if (event.path("livemode").asBoolean() != properties.isLiveMode()
                || eventRepository.existsById(eventId)) {
            return;
        }
        String type = event.path("type").asText();
        JsonNode data = event.path("data").path("object");
        if (type.startsWith("checkout.session.")) {
            sessionEvent(eventId, data);
        } else if ("invoice.payment_succeeded".equals(type)) {
            invoiceEvent(eventId, stripe.invoice(data.path("id").asText()), null);
        } else if ("charge.refunded".equals(type)) {
            String invoice = data.path("invoice").asText();
            if (invoice.startsWith("in_")) {
                invoiceEvent(eventId, stripe.invoice(invoice), data);
            }
        } else if ("customer.subscription.updated".equals(type)
                || "customer.subscription.deleted".equals(type)) {
            subscriptionEvent(eventId, data);
        }
    }

    private void sessionEvent(String eventId, JsonNode data) {
        // Retrieve current state: an old expiry event must not undo a completed subscription.
        JsonNode session = stripe.session(data.path("id").asText());
        WebCheckout checkout = lockedCheckout(eventId,
                session.path("metadata").path("checkout_id").asText());
        if (checkout == null) {
            return;
        }
        reservations.applySession(checkout, session);
        recordEvent(eventId);
    }

    private void subscriptionEvent(String eventId, JsonNode data) {
        JsonNode subscription = stripe.subscription(data.path("id").asText());
        WebCheckout checkout = lockedCheckout(eventId,
                subscription.path("metadata").path("checkout_id").asText());
        if (checkout == null) {
            return;
        }
        requireMode(subscription);
        reservations.bindSubscription(checkout, subscription.path("id").asText());
        recordEvent(eventId);
    }

    private void invoiceEvent(String eventId, JsonNode invoice, JsonNode refund) {
        JsonNode subscription = invoice.path("subscription");
        if (!subscription.isObject()) {
            return;
        }
        WebCheckout checkout = lockedCheckout(eventId,
                subscription.path("metadata").path("checkout_id").asText());
        if (checkout == null) {
            return;
        }
        requireMode(invoice);
        if (!invoice.path("paid").asBoolean() || invoice.path("paid_out_of_band").asBoolean()
                || invoice.path("amount_paid").asLong() < 0
                || !invoice.path("currency").asText().matches("[a-z]{3}")) {
            throw invalid();
        }
        reservations.bindSubscription(checkout, subscription.path("id").asText());
        String id = invoice.path("id").asText();
        WebPayment payment = paymentRepository.findById(id).orElse(null);
        if (payment == null) {
            payment = payment(invoice, checkout);
            payment = paymentRepository.save(payment);
        } else if (!payment.getCheckoutId().equals(checkout.getId())) {
            throw invalid();
        }
        if (refund != null) {
            requireMode(refund);
            WebPaymentRefund existing = refundRepository.findById(refund.path("id").asText())
                    .orElse(null);
            long amount = Math.max(0, refund.path("amount_refunded").asLong());
            if (existing == null) {
                existing = WebPaymentRefund.builder().id(refund.path("id").asText())
                        .invoiceId(id).amount(amount).build();
            } else {
                if (!existing.getInvoiceId().equals(id)) {
                    throw invalid();
                }
                existing.setAmount(Math.max(existing.getAmount(), amount));
            }
            refundRepository.save(existing);
            payment.setRefundedAmount(Math.min(payment.getAmountPaid(),
                    refundRepository.findByInvoiceId(id).stream()
                        .mapToLong(WebPaymentRefund::getAmount).sum()));
        }
        List<WebPayment> payments = paymentRepository
                .findByCheckoutIdOrderByPaidAtAscIdAsc(checkout.getId());
        commissions.recalculate(checkout, payments);
        paymentRepository.saveAll(payments);
        recordEvent(eventId);
    }

    private WebPayment payment(JsonNode invoice, WebCheckout checkout) {
        JsonNode lines = invoice.path("lines");
        JsonNode first = lines.path("data").path(0);
        String reason = invoice.path("billing_reason").asText();
        boolean recurring = ("subscription_create".equals(reason)
                || "subscription_cycle".equals(reason)) && lines.path("data").size() == 1
                && !lines.path("has_more").asBoolean() && !first.path("proration").asBoolean()
                && "subscription".equals(first.path("type").asText())
                && checkout.getPriceId().equals(first.path("price").path("id").asText());
        long paid = invoice.path("amount_paid").asLong();
        long taxes = 0;
        for (JsonNode tax : invoice.path("total_tax_amounts")) {
            taxes += tax.path("amount").asLong();
        }
        long base = invoice.path("total_excluding_tax").isNumber()
                ? invoice.path("total_excluding_tax").asLong() : paid - taxes;
        long periodStart = first.path("period").path("start").asLong();
        long paidAt = invoice.path("status_transitions").path("paid_at").asLong();
        if (!invoice.path("id").asText().startsWith("in_") || paidAt <= 0
                || (recurring && periodStart <= 0)) {
            throw invalid();
        }
        return WebPayment.builder().id(invoice.path("id").asText())
                .checkoutId(checkout.getId()).amountPaid(paid)
                .commissionBase(Math.min(paid, Math.max(0, base)))
                .currency(invoice.path("currency").asText()).paidAt(Instant.ofEpochSecond(paidAt))
                .periodStart(Instant.ofEpochSecond(periodStart)).recurringPeriod(recurring).build();
    }

    private WebCheckout lockedCheckout(String eventId, String id) {
        Long userId = checkoutRepository.findUserIdById(id).orElse(null);
        if (userId == null) {
            return null;
        }
        boolean ownerExists = userRepository.findByIdForUpdate(userId).isPresent();
        WebCheckout locked = checkoutRepository.findByIdForUpdate(id).orElseThrow();
        // Load the financial row only after the owner lock, avoiding stale deletion markers.
        if (!ownerExists && locked.getAccountDeletedAt() == null) {
            throw invalid();
        }
        return eventRepository.existsById(eventId) ? null : locked;
    }

    private void requireMode(JsonNode object) {
        if (!object.path("livemode").isBoolean()
                || object.path("livemode").asBoolean() != properties.isLiveMode()) {
            throw invalid();
        }
    }

    private void recordEvent(String id) {
        eventRepository.save(WebBillingEvent.builder().id(id).processedAt(Instant.now()).build());
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STRIPE_EVENT");
    }
}
