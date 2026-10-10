package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebBillingEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRefundRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(properties = "spring.liquibase.enabled=false", showSql = false)
class WebCheckoutRepositoryTest {
    @Autowired
    private WebCheckoutRepository repository;
    @Autowired
    private UserRepository users;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private WebPaymentRepository payments;
    @Autowired
    private WebPaymentRefundRepository refunds;
    @Autowired
    private WebBillingEventRepository events;

    @Test
    void refundBeforePaymentPersistsReducedCommissionThroughJpaMerge() throws Exception {
        WebCheckout checkout = checkout();
        checkout.setAffiliateId(1L);
        checkout.setReferrerId(2L);
        checkout.setCommissionPercent(new BigDecimal("35"));
        checkout.setReferrerCommissionPercent(new BigDecimal("15"));
        repository.saveAndFlush(checkout);
        ObjectMapper mapper = new ObjectMapper();
        StripeBillingClient stripe = mock(StripeBillingClient.class);
        when(stripe.invoice("in_paid")).thenReturn(mapper.readTree("""
                {"id":"in_paid","livemode":false,"paid":true,"amount_paid":12000,
                 "currency":"usd","total_excluding_tax":10000,
                 "billing_reason":"subscription_cycle",
                 "status_transitions":{"paid_at":1735689600},
                 "subscription":{"id":"sub_owned","metadata":{"checkout_id":"attempt"}},
                 "lines":{"has_more":false,"data":[{"type":"subscription","proration":false,
                   "price":{"id":"price_year"},"period":{"start":1735689600}}]}}
                """));
        WebBillingProperties properties = new WebBillingProperties();
        var reservations = new WebCheckoutReservationService(properties, new RevenueCatProperties(),
                users, repository, null, null, null, null, stripe);
        var service = new StripeWebhookService(mapper, mock(StripeSignatureVerifier.class), stripe,
                properties, repository, events, payments, refunds, users, reservations,
                new WebCommissionService());
        String json = """
                {"id":"evt_refund","livemode":false,"type":"charge.refunded","data":{"object":{
                  "id":"ch_paid","invoice":"in_paid","livemode":false,"amount_refunded":6000}}}
                """;
        service.handle(json.getBytes(StandardCharsets.UTF_8), "verified separately");
        entityManager.flush();
        entityManager.clear();
        var payment = payments.findById("in_paid").orElseThrow();
        assertThat(payment.getRefundedAmount()).isEqualTo(6000);
        assertThat(payment.getCommissionAmount()).isEqualTo(1750);
        assertThat(payment.getReferrerCommissionAmount()).isEqualTo(750);
    }

    @Test
    void receiptAcknowledgementCannotEraseANewerSyncRequest() {
        WebCheckout checkout = checkout();
        checkout.setState(WebCheckoutState.COMPLETED);
        checkout.setStripeSubscriptionId("sub_owned");
        Instant first = Instant.parse("2026-01-01T00:00:00Z");
        Instant second = first.plusSeconds(1);
        checkout.setReceiptSyncRequestedAt(second);
        repository.saveAndFlush(checkout);
        entityManager.clear();
        repository.markReceiptSynced(checkout.getId(), "sub_owned", Instant.now(), first);
        assertThat(repository.pendingReceiptSync(WebCheckoutState.COMPLETED,
                PageRequest.of(0, 20))).hasSize(1);
        repository.markReceiptSynced(checkout.getId(), "sub_owned", Instant.now(), second);
        entityManager.clear();
        assertThat(repository.pendingReceiptSync(WebCheckoutState.COMPLETED,
                PageRequest.of(0, 20))).isEmpty();
    }

    @Test
    void retainedDeletedCheckoutDoesNotEnterReceiptQueue() {
        WebCheckout checkout = checkout();
        checkout.setState(WebCheckoutState.COMPLETED);
        checkout.setStripeSubscriptionId("sub_deleted");
        checkout.setAccountDeletedAt(Instant.now());
        repository.saveAndFlush(checkout);
        assertThat(repository.pendingReceiptSync(WebCheckoutState.COMPLETED,
                PageRequest.of(0, 20))).isEmpty();
        assertThat(repository.findUserIdById(checkout.getId())).contains(checkout.getUserId());
    }

    @Test
    void expiredCheckoutDoesNotReserveCampaignOrAppearPending() {
        WebCheckout checkout = checkout();
        checkout.setPromoCodeId(123L);
        checkout.setState(WebCheckoutState.EXPIRED);
        repository.saveAndFlush(checkout);
        assertThat(repository.findFirstByUserIdAndStateInOrderByCreatedAtDesc(checkout.getUserId(),
                List.of(WebCheckoutState.CREATING, WebCheckoutState.OPEN))).isEmpty();
        assertThat(repository.countByPromoCodeIdAndStateIn(123L, List.of(
                WebCheckoutState.CREATING, WebCheckoutState.OPEN, WebCheckoutState.COMPLETED)))
                .isZero();
    }

    private WebCheckout checkout() {
        User user = new User();
        user.setEmail("web-repository-test@example.com");
        users.saveAndFlush(user);
        return WebCheckout.builder().id("attempt").userId(user.getId())
                .plan(WebBillingPlan.YEARLY).state(WebCheckoutState.OPEN).priceId("price_year")
                .successUrl("https://example.com/success").cancelUrl("https://example.com/cancel")
                .acquisitionType(PromoAcquisitionType.DIRECT).commissionPercent(BigDecimal.ZERO)
                .referrerCommissionPercent(BigDecimal.ZERO).createdAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600)).build();
    }
}
