package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.properties.AppCompatibilityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LegacyAccessPolicy {
    public static final int MISSING_VERSION_CODE = 0;

    private final AppCompatibilityProperties properties;

    public boolean grantsFreeProAccess(String appVersionCodeHeader) {
        if (!properties.isLegacyFreeAccessEnabled()) {
            return false;
        }
        Integer versionCode = parseVersionCode(appVersionCodeHeader);
        return versionCode != null
                && properties.getLegacyFreeAndroidVersionCodes() != null
                && properties.getLegacyFreeAndroidVersionCodes().contains(versionCode);
    }

    private Integer parseVersionCode(String value) {
        if (value == null || value.isBlank()) {
            return MISSING_VERSION_CODE;
        }
        try {
            int versionCode = Integer.parseInt(value.trim());
            return versionCode >= 0 ? versionCode : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
