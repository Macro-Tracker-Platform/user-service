package com.olehprukhnytskyi.macrotrackeruserservice.model;

import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "web_checkouts")
public class WebCheckout {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WebBillingPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WebCheckoutState state;

    @Column(nullable = false)
    private String priceId;

    @Column(nullable = false, columnDefinition = "text")
    private String successUrl;

    @Column(nullable = false, columnDefinition = "text")
    private String cancelUrl;

    private Long promoCodeId;

    @Column(length = 64)
    private String promoCode;

    @Column(nullable = false)
    private int discountPercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PromoAcquisitionType acquisitionType;

    private Long affiliateId;

    private Long referrerId;

    private String referrerName;

    private String partnerName;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal referrerCommissionPercent;

    @Column(nullable = false)
    private int trialDays;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(unique = true)
    private String stripeSessionId;

    @Column(columnDefinition = "text")
    private String checkoutUrl;

    @Column(unique = true)
    private String stripeSubscriptionId;

    private Instant accountDeletedAt;

    private Instant receiptSyncedAt;

    private Instant receiptSyncRequestedAt;
}
