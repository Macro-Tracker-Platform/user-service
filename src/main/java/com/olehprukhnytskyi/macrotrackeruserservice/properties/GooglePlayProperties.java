package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "google.play")
public class GooglePlayProperties {
    private String packageName;
    private Set<String> productIds = new HashSet<>();
    private String purchaseTokenKey;
    private String rtdnAudience;
    private String rtdnServiceAccountEmail;
    private ZoneId quotaZone = ZoneId.of("UTC");
}
