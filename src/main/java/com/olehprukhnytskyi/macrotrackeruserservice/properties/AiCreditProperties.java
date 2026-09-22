package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import jakarta.validation.constraints.Min;
import java.time.ZoneId;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ai-credits")
public class AiCreditProperties {
    @Min(1)
    private int freeDailyLimit = 3;
    private ZoneId quotaZone = ZoneId.of("UTC");
}
