package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPayment;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPaymentRefund;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebBillingEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRefundRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StripeWebhookServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final StripeBillingClient stripe = mock(StripeBillingClient.class);
    private final StripeSignatureVerifier signatures = mock(StripeSignatureVerifier.class);
    private final UserRepository users = mock(UserRepository.class);
    private final WebCheckoutRepository checkouts = mock(WebCheckoutRepository.class);
    private final WebBillingEventRepository events = mock(WebBillingEventRepository.class);
    private final WebPaymentRepository payments = mock(WebPaymentRepository.class);
    private final WebPaymentRefundRepository refunds = mock(WebPaymentRefundRepository.class);
    private final Map<String, WebPayment> paid = new HashMap<>();
    private final Map<String, WebPaymentRefund> returned = new HashMap<>();
    private final Set<String> processed = new HashSet<>();
    private final WebCheckout checkout = WebCheckout.builder().id("attempt").userId(42L)
            .plan(WebBillingPlan.YEARLY).priceId("price_year").state(WebCheckoutState.OPEN)
            .affiliateId(1L).referrerId(2L).commissionPercent(new BigDecimal("35"))
            .referrerCommissionPercent(new BigDecimal("15")).build();
    private StripeWebhookService service;

    @BeforeEach
    void setup() {
        WebBillingProperties properties = new WebBillingProperties();
        var reservations = new WebCheckoutReservationService(properties, new RevenueCatProperties(),
                users, checkouts, null, null, null, null, stripe);
        service = new StripeWebhookService(mapper, signatures, stripe, properties,
                checkouts, events, payments, refunds, users, reservations,
                new WebCommissionService());
        lenient().when(checkouts.findUserIdById("attempt")).thenReturn(Optional.of(42L));
        lenient().when(checkouts.findByIdForUpdate("attempt")).thenReturn(Optional.of(checkout));
        lenient().when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        lenient().when(events.existsById(any())).thenAnswer(call ->
                processed.contains(call.getArgument(0)));
        lenient().when(events.save(any())).thenAnswer(call -> {
            com.olehprukhnytskyi.macrotrackeruserservice.model.WebBillingEvent event =
                    call.getArgument(0);
            processed.add(event.getId());
            return event;
        });
        lenient().when(payments.findById(any())).thenAnswer(call ->
                Optional.ofNullable(paid.get(call.getArgument(0))));
        lenient().when(payments.save(any())).thenAnswer(call -> {
            WebPayment payment = call.getArgument(0);
            paid.put(payment.getId(), payment);
            return payment;
        });
        lenient().when(payments.findByCheckoutIdOrderByPaidAtAscIdAsc("attempt"))
                .thenAnswer(call -> paid.values().stream().toList());
        lenient().when(refunds.findById(any())).thenAnswer(call ->
                Optional.ofNullable(returned.get(call.getArgument(0))));
        lenient().when(refunds.save(any())).thenAnswer(call -> {
            WebPaymentRefund refund = call.getArgument(0);
            returned.put(refund.getId(), refund);
            return refund;
        });
        lenient().when(refunds.findByInvoiceId(any())).thenAnswer(call ->
                returned.values().stream().toList());
    }

    @Test
    void signedDuplicatePaymentProducesOneInvoiceAndCorrectNetOfTaxCommissions() throws Exception {
        when(stripe.invoice("in_paid")).thenReturn(invoice(12000));
        event("evt_paid", "invoice.payment_succeeded", "{\"id\":\"in_paid\"}");
        event("evt_paid", "invoice.payment_succeeded", "{\"id\":\"in_paid\"}");
        assertThat(paid).hasSize(1);
        assertThat(paid.get("in_paid").getCommissionAmount()).isEqualTo(3500);
        assertThat(paid.get("in_paid").getReferrerCommissionAmount()).isEqualTo(1500);
        assertThat(checkout.getStripeSubscriptionId()).isEqualTo("sub_owned");
        assertThat(checkout.getReceiptSyncedAt()).isNull();
        assertThat(checkout.getReceiptSyncRequestedAt()).isNotNull();
    }

    @Test
    void refundBeforePaymentAndStaleRefundDoNotIncreaseCommission() throws Exception {
        when(stripe.invoice("in_paid")).thenReturn(invoice(12000));
        event("evt_refund", "charge.refunded", refund(6000));
        assertThat(paid.get("in_paid").getCommissionAmount()).isEqualTo(1750);
        assertThat(paid.get("in_paid").getReferrerCommissionAmount()).isEqualTo(750);
        event("evt_stale_refund", "charge.refunded", refund(1000));
        event("evt_paid", "invoice.payment_succeeded", "{\"id\":\"in_paid\"}");
        assertThat(paid).hasSize(1);
        assertThat(returned).hasSize(1);
        assertThat(paid.get("in_paid").getRefundedAmount()).isEqualTo(6000);
        assertThat(paid.get("in_paid").getCommissionAmount()).isEqualTo(1750);
    }

    @Test
    void latePaymentAndRefundPreserveCommissionsForDeletedAccount() throws Exception {
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        checkout.setAccountDeletedAt(java.time.Instant.now());
        when(stripe.invoice("in_paid")).thenReturn(invoice(12000));
        event("evt_deleted_paid", "invoice.payment_succeeded", "{\"id\":\"in_paid\"}");
        event("evt_deleted_refund", "charge.refunded", refund(6000));
        assertThat(paid.get("in_paid").getCommissionAmount()).isEqualTo(1750);
        assertThat(paid.get("in_paid").getReferrerCommissionAmount()).isEqualTo(750);
        assertThat(checkout.getAccountDeletedAt()).isNotNull();
    }

    @Test
    void trialInvoiceDoesNotAccrueCommission() throws Exception {
        when(stripe.invoice("in_paid")).thenReturn(invoice(0));
        event("evt_trial", "invoice.payment_succeeded", "{\"id\":\"in_paid\"}");
        assertThat(paid.get("in_paid").getCommissionAmount()).isZero();
        assertThat(paid.get("in_paid").getReferrerCommissionAmount()).isZero();
    }

    @Test
    void wrongEnvironmentIsIgnoredBeforeReadingProviderState() {
        String json = "{\"id\":\"evt_live\",\"livemode\":true,"
                + "\"type\":\"invoice.payment_succeeded\",\"data\":{\"object\":{}}}";
        service.handle(json.getBytes(StandardCharsets.UTF_8), "verified separately");
        verifyNoInteractions(stripe, payments, refunds);
    }

    private void event(String id, String type, String object) {
        String json = "{\"id\":\"" + id + "\",\"livemode\":false,\"type\":\"" + type
                + "\",\"data\":{\"object\":" + object + "}}";
        service.handle(json.getBytes(StandardCharsets.UTF_8), "verified separately");
    }

    private String refund(long amount) {
        return "{\"id\":\"ch_paid\",\"invoice\":\"in_paid\",\"livemode\":false,"
                + "\"amount_refunded\":" + amount + "}";
    }

    private JsonNode invoice(long amount) throws Exception {
        return mapper.readTree("""
                {"id":"in_paid","livemode":false,"paid":true,"paid_out_of_band":false,
                 "amount_paid":%d,"currency":"usd","total_excluding_tax":10000,
                 "billing_reason":"subscription_cycle",
                 "status_transitions":{"paid_at":1735689600},
                 "subscription":{"id":"sub_owned","metadata":{"checkout_id":"attempt"}},
                 "lines":{"has_more":false,"data":[{"type":"subscription","proration":false,
                   "price":{"id":"price_year"},"period":{"start":1735689600}}]}}
                """.formatted(amount));
    }
}
