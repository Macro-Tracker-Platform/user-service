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
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserEntitlement;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.BillingEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserEntitlementRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import com.olehprukhnytskyi.util.UserRole;
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
    private UserRepository userRepository;
    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private BillingEventRepository billingEventRepository;
    @Mock
    private GooglePlayApiClient googlePlayApiClient;
    @Mock
    private GooglePubSubTokenVerifier pubSubTokenVerifier;
    @Mock
    private PurchaseTokenCipher tokenCipher;
    @Mock
    private PromoCodeService promoCodeService;
    @Mock
    private TrialEligibilityService trialEligibilityService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private SubscriptionService subscriptionService;

    @BeforeEach
    void setUp() {
        lenient().when(subscriptionRepository.findByUserIdOrderByExpiresAtDesc(USER_ID))
                .thenReturn(List.of());
        lenient().when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.get(anyString())).thenReturn(null);
        GooglePlayProperties googlePlayProperties = new GooglePlayProperties();
        googlePlayProperties.getProductIds().add("macro_tracker_pro");
        subscriptionService = new SubscriptionService(
                subscriptionRepository,
                userRepository,
                entitlementRepository,
                billingEventRepository,
                googlePlayApiClient,
                pubSubTokenVerifier,
                tokenCipher,
                promoCodeService,
                trialEligibilityService,
                googlePlayProperties,
                redisTemplate,
                new ObjectMapper());
    }

    @Test
    void userWithoutSubscriptionKeepsFreeLimits() {
        EntitlementResponseDto entitlement = subscriptionService.getEntitlement(USER_ID);

        assertThat(entitlement.getPlan()).isEqualTo("FREE");
        assertThat(entitlement.isLegacyAccess()).isFalse();
        assertThat(entitlement.getFeatures().getNutritionLabelScans().getLimit())
                .isEqualTo(3);
        assertThat(entitlement.getFeatures().isAdvancedInsights()).isFalse();
    }

    @Test
    void revenueCatSubscribedUserGetsProEntitlement() {
        UserEntitlement revenueCatEntitlement = UserEntitlement.builder()
                .userId(USER_ID)
                .subscribed(true)
                .subscriptionEventTimestampMs(1000L)
                .build();
        when(entitlementRepository.findById(USER_ID))
                .thenReturn(Optional.of(revenueCatEntitlement));

        EntitlementResponseDto entitlement = subscriptionService.getEntitlement(USER_ID);

        assertThat(entitlement.getPlan()).isEqualTo("PRO");
        assertThat(entitlement.getState()).isEqualTo(SubscriptionStatus.PRO_ACTIVE);
        assertThat(entitlement.getFeatures().isAdvancedInsights()).isTrue();
    }

    @Test
    void revenueCatExpirationOverridesLegacySubscription() {
        UserEntitlement revenueCatEntitlement = UserEntitlement.builder()
                .userId(USER_ID)
                .subscribed(false)
                .subscriptionEventTimestampMs(2000L)
                .build();
        when(entitlementRepository.findById(USER_ID))
                .thenReturn(Optional.of(revenueCatEntitlement));
        Subscription legacy = Subscription.builder()
                .userId(USER_ID)
                .status(SubscriptionStatus.PRO_ACTIVE)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        lenient().when(subscriptionRepository.findByUserIdOrderByExpiresAtDesc(USER_ID))
                .thenReturn(List.of(legacy));

        EntitlementResponseDto entitlement = subscriptionService.getEntitlement(USER_ID);

        assertThat(entitlement.getPlan()).isEqualTo("FREE");
        assertThat(entitlement.getFeatures().isAdvancedInsights()).isFalse();
    }

    @Test
    void adminRoleHeaderGetsLifetimeProEntitlementWithoutDatabaseRole() {
        EntitlementResponseDto entitlement = subscriptionService
                .getEntitlement(USER_ID, "USER,ADMIN");

        assertThat(entitlement.getPlan()).isEqualTo("PRO");
        assertThat(entitlement.getState()).isEqualTo(SubscriptionStatus.PRO_ACTIVE);
        assertThat(entitlement.getValidUntil()).isNull();
        assertThat(entitlement.getFeatures().isAdvancedInsights()).isTrue();
        assertThat(entitlement.getFeatures().isFuturePlanning()).isTrue();
    }

    @Test
    void adminGetsLifetimeProEntitlementWithoutSubscription() {
        User admin = new User();
        admin.addRole(UserRole.ADMIN);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(admin));

        EntitlementResponseDto entitlement = subscriptionService
                .getEntitlement(USER_ID);

        assertThat(entitlement.getPlan()).isEqualTo("PRO");
        assertThat(entitlement.getState()).isEqualTo(SubscriptionStatus.PRO_ACTIVE);
        assertThat(entitlement.getValidUntil()).isNull();
        assertThat(entitlement.getFeatures().isFuturePlanning()).isTrue();
        assertThat(entitlement.getFeatures().isWeekdayGoals()).isTrue();
        assertThat(entitlement.getFeatures().isAdaptiveCalories()).isTrue();
    }

    @Test
    void vipGetsLifetimeProEntitlementWithoutSubscription() {
        User vip = new User();
        vip.addRole(UserRole.VIP);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(vip));

        EntitlementResponseDto entitlement = subscriptionService
                .getEntitlement(USER_ID);

        assertThat(entitlement.getPlan()).isEqualTo("PRO");
        assertThat(entitlement.getState()).isEqualTo(SubscriptionStatus.PRO_ACTIVE);
        assertThat(entitlement.getValidUntil()).isNull();
        assertThat(entitlement.getFeatures().isFuturePlanning()).isTrue();
    }

    @Test
    void verifyKeepsExistingGooglePlayPurchaseWithLatestUserByDefault() {
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
                null,
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

        EntitlementResponseDto entitlement = subscriptionService.verify(USER_ID, purchase, null);

        assertThat(existing.getUserId()).isEqualTo(USER_ID);
        assertThat(entitlement.getPlan()).isEqualTo("PRO");
        verify(subscriptionRepository).save(existing);
    }
}
