package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import java.time.ZoneId;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "barcode-scan")
public class BarcodeScanProperties {
    private int freeDailyUniqueLimit = 5;
    private ZoneId quotaZone = ZoneId.of("UTC");
}
