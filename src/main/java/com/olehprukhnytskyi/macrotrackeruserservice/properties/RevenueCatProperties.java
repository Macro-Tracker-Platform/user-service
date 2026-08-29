package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "revenuecat")
public class RevenueCatProperties {
    private String webhookAuthorization;
}
