package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.BillingEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceEntitlementTest {
    private static final Long USER_ID = 42L;

    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private BillingEventRepository billingEventRepository;
    @Mock
    private GooglePlayApiClient googlePlayApiClient;
    @Mock
    private GooglePubSubTokenVerifier pubSubTokenVerifier;
    @Mock
    private PurchaseTokenCipher tokenCipher;
    @Mock
    private LegacyAccessPolicy legacyAccessPolicy;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        when(subscriptionRepository.findByUserIdOrderByExpiresAtDesc(USER_ID))
                .thenReturn(List.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                billingEventRepository,
                googlePlayApiClient,
                pubSubTokenVerifier,
                tokenCipher,
                legacyAccessPolicy,
                new GooglePlayProperties(),
                redisTemplate,
                new ObjectMapper());
    }

    @Test
    void legacyClientGetsTemporaryProFeaturesForFree() {
        when(legacyAccessPolicy.grantsFreeProAccess(null)).thenReturn(true);

        EntitlementResponseDto entitlement = subscriptionService
                .getEntitlement(USER_ID, null);

        assertThat(entitlement.getPlan()).isEqualTo("LEGACY_FREE");
        assertThat(entitlement.isLegacyAccess()).isTrue();
        assertThat(entitlement.getFeatures().getNutritionLabelScans().getLimit())
                .isEqualTo(60);
        assertThat(entitlement.getFeatures().isAdvancedInsights()).isTrue();
        assertThat(entitlement.getFeatures().isFuturePlanning()).isFalse();
        assertThat(entitlement.getFeatures().isWeekdayGoals()).isFalse();
        assertThat(entitlement.getFeatures().isAdaptiveCalories()).isFalse();
    }

    @Test
    void currentClientWithoutSubscriptionKeepsFreeLimits() {
        when(legacyAccessPolicy.grantsFreeProAccess("42")).thenReturn(false);

        EntitlementResponseDto entitlement = subscriptionService
                .getEntitlement(USER_ID, "42");

        assertThat(entitlement.getPlan()).isEqualTo("FREE");
        assertThat(entitlement.isLegacyAccess()).isFalse();
        assertThat(entitlement.getFeatures().getNutritionLabelScans().getLimit())
                .isEqualTo(3);
        assertThat(entitlement.getFeatures().isAdvancedInsights()).isFalse();
    }
}
