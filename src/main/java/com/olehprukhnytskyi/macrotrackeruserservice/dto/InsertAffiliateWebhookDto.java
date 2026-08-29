package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class InsertAffiliateWebhookDto {
    @JsonAlias({"event_type", "event"})
    private String eventType;

    private String action;
    private String status;
    private Boolean active;

    @JsonAlias({"affiliate_id", "id"})
    private String affiliateId;

    @JsonAlias({"short_code", "shortCode", "coupon_code", "couponCode"})
    private String shortCode;

    private String code;
    private String name;
    private String email;

    @JsonAlias({"deep_link", "deepLink", "affiliate_link", "affiliateLink"})
    private String deepLink;

    @JsonAlias({"occurred_at", "occurredAt"})
    private String occurredAt;

    @JsonAlias({"commission_percent", "commissionPercent"})
    private BigDecimal commissionPercent;

    @JsonAlias({"discount_percent", "discountPercent"})
    private Integer discountPercent;

    @JsonAlias({"monthly_offer_id", "monthlyOfferId"})
    private String monthlyOfferId;

    @JsonAlias({"yearly_offer_id", "yearlyOfferId"})
    private String yearlyOfferId;

    private Affiliate affiliate;

    @JsonAlias({"promo_code", "promoCode"})
    private PromoCodePayload promoCode;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Affiliate {
        @JsonAlias({"affiliate_id", "id"})
        private String affiliateId;

        @JsonAlias({"short_code", "shortCode", "coupon_code", "couponCode"})
        private String shortCode;

        private String code;
        private String name;
        private String email;
        private String status;
        private Boolean active;

        @JsonAlias({"deep_link", "deepLink", "affiliate_link", "affiliateLink"})
        private String deepLink;

        @JsonAlias({"commission_percent", "commissionPercent"})
        private BigDecimal commissionPercent;

        @JsonAlias({"discount_percent", "discountPercent"})
        private Integer discountPercent;

        @JsonAlias({"monthly_offer_id", "monthlyOfferId"})
        private String monthlyOfferId;

        @JsonAlias({"yearly_offer_id", "yearlyOfferId"})
        private String yearlyOfferId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromoCodePayload {
        @JsonAlias({"short_code", "shortCode", "coupon_code", "couponCode"})
        private String shortCode;

        private String code;
        private String status;
        private Boolean active;

        @JsonAlias({"discount_percent", "discountPercent"})
        private Integer discountPercent;

        @JsonAlias({"monthly_offer_id", "monthlyOfferId"})
        private String monthlyOfferId;

        @JsonAlias({"yearly_offer_id", "yearlyOfferId"})
        private String yearlyOfferId;
    }
}
