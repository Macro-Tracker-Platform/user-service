package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GooglePurchaseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.BillingEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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
    private GooglePlayProperties googlePlayProperties;

    @BeforeEach
    void setUp() {
        lenient().when(subscriptionRepository.findByUserIdOrderByExpiresAtDesc(USER_ID))
                .thenReturn(List.of());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        googlePlayProperties = new GooglePlayProperties();
        googlePlayProperties.getProductIds().add("macro_tracker_pro");
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                billingEventRepository,
                googlePlayApiClient,
                pubSubTokenVerifier,
                tokenCipher,
                legacyAccessPolicy,
                googlePlayProperties,
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
                .isEqualTo(30);
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

    @Test
    void verifyClaimsExistingGooglePlayPurchaseForCurrentUser() {
        Long previousUserId = 7L;
        String token = "purchase-token";
        String hash = "purchase-token-hash";
        Instant expiresAt = Instant.now().plusSeconds(3600);
        GooglePurchaseDto purchase = new GooglePurchaseDto();
        purchase.setProductId("macro_tracker_pro");
        purchase.setPurchaseToken(token);
        Subscription existing = Subscription.builder()
                .id(1L)
                .userId(previousUserId)
                .provider("GOOGLE_PLAY")
                .productId("macro_tracker_pro")
                .purchaseTokenHash(hash)
                .purchaseTokenEncrypted("encrypted-token")
                .status(SubscriptionStatus.PRO_ACTIVE)
                .expiresAt(expiresAt)
                .build();
        GooglePlaySubscriptionSnapshot snapshot = new GooglePlaySubscriptionSnapshot(
                "macro_tracker_pro",
                "monthly",
                "SUBSCRIPTION_STATE_ACTIVE",
                Instant.now().minusSeconds(60),
                expiresAt,
                true,
                true);
        when(googlePlayApiClient.getSubscription(token)).thenReturn(snapshot);
        when(tokenCipher.hash(token)).thenReturn(hash);
        when(subscriptionRepository.findByPurchaseTokenHash(hash))
                .thenReturn(Optional.of(existing));
        when(subscriptionRepository.findByUserIdOrderByExpiresAtDesc(USER_ID))
                .thenReturn(List.of(existing));

        EntitlementResponseDto entitlement = subscriptionService.verify(USER_ID, purchase, "42");

        assertThat(existing.getUserId()).isEqualTo(USER_ID);
        assertThat(entitlement.getPlan()).isEqualTo("PRO");
        verify(subscriptionRepository).save(existing);
    }
}
