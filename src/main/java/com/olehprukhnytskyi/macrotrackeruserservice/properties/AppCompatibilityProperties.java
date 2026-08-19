package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.compatibility")
public class AppCompatibilityProperties {
    private boolean legacyFreeAccessEnabled = true;
    private Set<Integer> legacyFreeAndroidVersionCodes = new HashSet<>(Set.of(0));
}
