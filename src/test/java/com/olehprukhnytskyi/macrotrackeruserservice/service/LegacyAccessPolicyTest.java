package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackeruserservice.properties.AppCompatibilityProperties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LegacyAccessPolicyTest {
    private AppCompatibilityProperties properties;
    private LegacyAccessPolicy policy;

    @BeforeEach
    void setUp() {
        properties = new AppCompatibilityProperties();
        properties.setLegacyFreeAccessEnabled(true);
        properties.setLegacyFreeAndroidVersionCodes(Set.of(0, 41));
        policy = new LegacyAccessPolicy(properties);
    }

    @Test
    void missingVersionHeaderRepresentsLegacyClient() {
        assertThat(policy.grantsFreeProAccess(null)).isTrue();
        assertThat(policy.grantsFreeProAccess("  ")).isTrue();
    }

    @Test
    void allowlistedVersionGetsLegacyAccess() {
        assertThat(policy.grantsFreeProAccess("41")).isTrue();
    }

    @Test
    void currentOrInvalidVersionDoesNotGetLegacyAccess() {
        assertThat(policy.grantsFreeProAccess("42")).isFalse();
        assertThat(policy.grantsFreeProAccess("not-a-number")).isFalse();
        assertThat(policy.grantsFreeProAccess("-1")).isFalse();
    }

    @Test
    void killSwitchDisablesAllLegacyAccess() {
        properties.setLegacyFreeAccessEnabled(false);

        assertThat(policy.grantsFreeProAccess(null)).isFalse();
        assertThat(policy.grantsFreeProAccess("41")).isFalse();
    }
}
