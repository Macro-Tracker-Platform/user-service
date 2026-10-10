package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "web-billing")
public class WebBillingProperties {
    private boolean enabled;
    private String stripeSecretKey;
    private String stripeWebhookSecret;
    private boolean liveMode;
    private String monthlyPriceId;
    private String yearlyPriceId;
    private String successUrl;
    private String cancelUrl;
    private String portalReturnUrl;
    private String mobilePortalConfigurationId;
    private String mobilePortalReturnUrl;
    private int yearlyTrialDays = 7;
    private Duration checkoutLifetime = Duration.ofHours(1);
}
