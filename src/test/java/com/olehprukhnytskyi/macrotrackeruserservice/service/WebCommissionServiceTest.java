package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPayment;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebCommissionServiceTest {
    private final WebCommissionService service = new WebCommissionService();

    @Test
    void yearlyPaysBothRecipientsOnlyForFirstPositiveRecurringPayment() {
        WebPayment trial = payment("trial", 0, 0);
        WebPayment first = payment("first", 10000, 1);
        WebPayment renewal = payment("renewal", 10000, 13);
        service.recalculate(checkout(WebBillingPlan.YEARLY), List.of(trial, first, renewal));
        assertThat(trial.getCommissionAmount()).isZero();
        assertThat(first.getCommissionAmount()).isEqualTo(3500);
        assertThat(first.getReferrerCommissionAmount()).isEqualTo(1500);
        assertThat(renewal.getCommissionAmount()).isZero();
        assertThat(renewal.getReferrerCommissionAmount()).isZero();
    }

    @Test
    void monthlyPaysFirstSixCalendarPeriodsAndExcludesSeventh() {
        List<WebPayment> payments = new ArrayList<>();
        for (int month = 0; month < 7; month++) {
            payments.add(payment("invoice" + month, 1000, month));
        }
        service.recalculate(checkout(WebBillingPlan.MONTHLY), payments);
        assertThat(payments.subList(0, 6)).allSatisfy(payment -> {
            assertThat(payment.getCommissionAmount()).isEqualTo(350);
            assertThat(payment.getReferrerCommissionAmount()).isEqualTo(150);
        });
        assertThat(payments.getLast().getCommissionAmount()).isZero();
    }

    @Test
    void pausedSubscriptionDoesNotRestartSixMonthWindow() {
        WebPayment first = payment("first", 1000, 0);
        WebPayment resumed = payment("resumed", 1000, 7);
        service.recalculate(checkout(WebBillingPlan.MONTHLY), List.of(first, resumed));
        assertThat(resumed.getCommissionAmount()).isZero();
    }

    @Test
    void lateInitialInvoiceReplacesAnnualRenewalCommission() {
        WebPayment first = payment("first", 10000, 0);
        WebPayment renewal = payment("renewal", 10000, 12);
        WebCheckout checkout = checkout(WebBillingPlan.YEARLY);
        service.recalculate(checkout, List.of(renewal));
        assertThat(renewal.getCommissionAmount()).isEqualTo(3500);
        service.recalculate(checkout, List.of(renewal, first));
        assertThat(renewal.getCommissionAmount()).isZero();
        assertThat(first.getCommissionAmount()).isEqualTo(3500);
    }

    @Test
    void refundedFirstPaymentDoesNotMoveAnnualCommissionToRenewal() {
        WebPayment first = payment("first", 10000, 0);
        first.setRefundedAmount(10000);
        WebPayment renewal = payment("renewal", 10000, 12);
        service.recalculate(checkout(WebBillingPlan.YEARLY), List.of(first, renewal));
        assertThat(first.getCommissionAmount()).isZero();
        assertThat(renewal.getCommissionAmount()).isZero();
    }

    @Test
    void partialRefundReducesNetOfTaxBaseForBothRecipients() {
        WebPayment payment = payment("paid", 12000, 0);
        payment.setCommissionBase(10000);
        payment.setRefundedAmount(6000);
        service.recalculate(checkout(WebBillingPlan.YEARLY), List.of(payment));
        assertThat(payment.getCommissionAmount()).isEqualTo(1750);
        assertThat(payment.getReferrerCommissionAmount()).isEqualTo(750);
    }

    @Test
    void directCampaignAndProrationDoNotEarnCommission() {
        WebCheckout direct = checkout(WebBillingPlan.YEARLY);
        direct.setAffiliateId(null);
        WebPayment payment = payment("paid", 10000, 0);
        service.recalculate(direct, List.of(payment));
        assertThat(payment.getCommissionAmount()).isZero();
        payment.setRecurringPeriod(false);
        service.recalculate(checkout(WebBillingPlan.YEARLY), List.of(payment));
        assertThat(payment.getCommissionAmount()).isZero();
    }

    @Test
    void missingReferrerDoesNotAccrueReferralCommission() {
        WebCheckout checkout = checkout(WebBillingPlan.YEARLY);
        checkout.setReferrerId(null);
        WebPayment payment = payment("paid", 10000, 0);
        service.recalculate(checkout, List.of(payment));
        assertThat(payment.getCommissionAmount()).isEqualTo(3500);
        assertThat(payment.getReferrerCommissionAmount()).isZero();
    }

    private WebCheckout checkout(WebBillingPlan plan) {
        return WebCheckout.builder().plan(plan).affiliateId(10L).referrerId(20L)
                .commissionPercent(new BigDecimal("35"))
                .referrerCommissionPercent(new BigDecimal("15")).build();
    }

    private WebPayment payment(String id, long amount, int month) {
        Instant date = Instant.parse("2025-01-01T00:00:00Z").atZone(ZoneOffset.UTC)
                .plusMonths(month).toInstant();
        return WebPayment.builder().id(id).amountPaid(amount).commissionBase(amount)
                .paidAt(date).periodStart(date).recurringPeriod(true).build();
    }
}
