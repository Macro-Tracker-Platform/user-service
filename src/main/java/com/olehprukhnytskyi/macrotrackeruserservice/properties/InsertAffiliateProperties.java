package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import java.math.BigDecimal;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "insert-affiliate")
public class InsertAffiliateProperties {
    private String webhookAuthorization;
    private BigDecimal defaultCommissionPercent = BigDecimal.ZERO;
    private Integer defaultDiscountPercent = 0;
    private String monthlyOfferId;
    private String yearlyOfferId;
}
