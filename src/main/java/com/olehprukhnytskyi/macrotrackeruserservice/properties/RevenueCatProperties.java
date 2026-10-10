package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "revenuecat")
public class RevenueCatProperties {
    private String webhookAuthorization;
    private String subscriberApiKey;
    private String stripeApiKey;
    private String promoYearlyProductId;
    private String promoMonthlyProductId;
    private String entitlementId = "macro_tracker_calorie_counter_premium";
    private String environment = "PRODUCTION";
}
