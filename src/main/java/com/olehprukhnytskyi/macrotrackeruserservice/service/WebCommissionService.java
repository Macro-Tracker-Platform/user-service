package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPayment;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WebCommissionService {
    public static final BigDecimal AFFILIATE_PERCENT = new BigDecimal("35.00");
    public static final BigDecimal REFERRER_PERCENT = new BigDecimal("15.00");

    public void recalculate(WebCheckout checkout, List<WebPayment> payments) {
        List<WebPayment> recurring = payments.stream()
                .filter(WebPayment::isRecurringPeriod)
                .filter(payment -> payment.getAmountPaid() > 0)
                .sorted(Comparator.comparing(WebPayment::getPeriodStart)
                        .thenComparing(WebPayment::getId)).toList();
        Instant limit = recurring.isEmpty() ? Instant.EPOCH
                : recurring.getFirst().getPeriodStart().atZone(ZoneOffset.UTC)
                    .plusMonths(6).toInstant();
        int max = checkout.getPlan() == WebBillingPlan.YEARLY ? 1 : 6;
        List<String> eligible = recurring.stream()
                .filter(payment -> checkout.getPlan() == WebBillingPlan.YEARLY
                        || payment.getPeriodStart().isBefore(limit))
                .limit(max).map(WebPayment::getId).toList();
        for (WebPayment payment : payments) {
            boolean attributed = checkout.getAffiliateId() != null
                    && eligible.contains(payment.getId());
            long remainingBase = remainingBase(payment);
            payment.setCommissionAmount(attributed
                    ? percent(remainingBase, checkout.getCommissionPercent()) : 0);
            payment.setReferrerCommissionAmount(attributed && checkout.getReferrerId() != null
                    ? percent(remainingBase, checkout.getReferrerCommissionPercent()) : 0);
        }
    }

    private long remainingBase(WebPayment payment) {
        if (payment.getAmountPaid() <= 0) {
            return 0;
        }
        long remaining = Math.max(0, payment.getAmountPaid() - payment.getRefundedAmount());
        return BigDecimal.valueOf(payment.getCommissionBase())
                .multiply(BigDecimal.valueOf(remaining))
                .divide(BigDecimal.valueOf(payment.getAmountPaid()), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private long percent(long amount, BigDecimal rate) {
        return BigDecimal.valueOf(amount).multiply(rate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).longValueExact();
    }
}
