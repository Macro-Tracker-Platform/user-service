package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GooglePurchaseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoogleRtdnRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.BillingEvent;
import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.BillingEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class SubscriptionService {
    private static final int FREE_SCAN_LIMIT = 3;
    private static final int PRO_SCAN_LIMIT = 60;
    private static final String PROVIDER = "GOOGLE_PLAY";
    private static final String MONTHLY_QUOTA_PREFIX = "nutrition-scan:monthly:";

    private final SubscriptionRepository subscriptionRepository;
    private final BillingEventRepository billingEventRepository;
    private final GooglePlayApiClient googlePlayApiClient;
    private final GooglePubSubTokenVerifier pubSubTokenVerifier;
    private final PurchaseTokenCipher tokenCipher;
    private final LegacyAccessPolicy legacyAccessPolicy;
    private final GooglePlayProperties properties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Transactional
    public EntitlementResponseDto verify(Long userId, GooglePurchaseDto purchase,
                                         String appVersionCode) {
        GooglePlaySubscriptionSnapshot snapshot = googlePlayApiClient
                .getSubscription(purchase.getPurchaseToken());
        validateProduct(purchase.getProductId(), snapshot.productId());
        String hash = tokenCipher.hash(purchase.getPurchaseToken());
        Subscription subscription = subscriptionRepository.findByPurchaseTokenHash(hash)
                .map(existing -> requireOwner(existing, userId))
                .orElseGet(() -> Subscription.builder()
                        .userId(userId)
                        .provider(PROVIDER)
                        .purchaseTokenHash(hash)
                        .purchaseTokenEncrypted(tokenCipher.encrypt(purchase.getPurchaseToken()))
                        .build());
        applySnapshot(subscription, snapshot);
        if (!snapshot.acknowledged() && grantsPro(subscription.getStatus())) {
            googlePlayApiClient.acknowledge(snapshot.productId(), purchase.getPurchaseToken());
            subscription.setAcknowledged(true);
        }
        subscriptionRepository.save(subscription);
        return getEntitlement(userId, appVersionCode);
    }

    @Transactional
    public EntitlementResponseDto restore(Long userId, List<GooglePurchaseDto> purchases,
                                          String appVersionCode) {
        for (GooglePurchaseDto purchase : purchases) {
            verify(userId, purchase, appVersionCode);
        }
        return getEntitlement(userId, appVersionCode);
    }

    @Transactional(readOnly = true)
    public EntitlementResponseDto getEntitlement(Long userId, String appVersionCode) {
        Subscription subscription = subscriptionRepository
                .findByUserIdOrderByExpiresAtDesc(userId)
                .stream()
                .max(Comparator.comparing(
                        item -> item.getExpiresAt() == null ? Instant.EPOCH : item.getExpiresAt()))
                .orElse(null);
        SubscriptionStatus status = subscription == null
                ? SubscriptionStatus.FREE : effectiveStatus(subscription);
        boolean pro = grantsPro(status);
        boolean legacyAccess = !pro
                && legacyAccessPolicy.grantsFreeProAccess(appVersionCode);
        boolean hasProFeatures = pro || legacyAccess;
        int limit = hasProFeatures ? PRO_SCAN_LIMIT : FREE_SCAN_LIMIT;
        ZonedDateTime now = ZonedDateTime.now(properties.getQuotaZone());
        YearMonth month = YearMonth.from(now);
        String key = MONTHLY_QUOTA_PREFIX + userId + ":" + month;
        int used = parseUsage(redisTemplate.opsForValue().get(key));
        Instant resetAt = month.plusMonths(1)
                .atDay(1)
                .atStartOfDay(properties.getQuotaZone())
                .toInstant();
        return EntitlementResponseDto.builder()
                .plan(pro ? "PRO" : legacyAccess ? "LEGACY_FREE" : "FREE")
                .state(status)
                .validUntil(subscription == null ? null : subscription.getExpiresAt())
                .legacyAccess(legacyAccess)
                .features(EntitlementResponseDto.Features.builder()
                        .nutritionLabelScans(EntitlementResponseDto.ScanAllowance.builder()
                                .limit(limit)
                                .remaining(Math.max(0, limit - used))
                                .resetAt(resetAt)
                                .build())
                        .advancedInsights(hasProFeatures)
                        .futurePlanning(hasProFeatures)
                        .weekdayGoals(hasProFeatures)
                        .adaptiveCalories(hasProFeatures)
                        .build())
                .build();
    }

    @Transactional
    public void processRtdn(GoogleRtdnRequestDto request) {
        if (request == null || request.getMessage() == null
                || request.getMessage().getMessageId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Pub/Sub message");
        }
        String messageId = request.getMessage().getMessageId();
        BillingEvent event = billingEventRepository.findByGoogleMessageId(messageId)
                .orElseGet(() -> billingEventRepository.save(BillingEvent.builder()
                        .googleMessageId(messageId)
                        .eventType("UNKNOWN")
                        .receivedAt(Instant.now())
                        .build()));
        if (event.getProcessedAt() != null) {
            return;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(request.getMessage().getData());
            JsonNode notification = objectMapper.readTree(
                    new String(decoded, StandardCharsets.UTF_8));
            if (!properties.getPackageName().equals(notification.path("packageName").asText())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "RTDN package name does not match");
            }
            JsonNode subscriptionNotification = notification.path("subscriptionNotification");
            if (subscriptionNotification.isMissingNode()) {
                event.setEventType(notification.has("testNotification") ? "TEST" : "IGNORED");
            } else {
                int notificationType = subscriptionNotification.path("notificationType").asInt();
                event.setEventType("SUBSCRIPTION_" + notificationType);
                refreshByToken(subscriptionNotification.path("purchaseToken").asText());
            }
            event.setProcessedAt(Instant.now());
            billingEventRepository.save(event);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not process Google Play RTDN", exception);
        }
    }

    public void verifyRtdnAuthorization(String authorization) {
        pubSubTokenVerifier.verify(authorization);
    }

    private void refreshByToken(String purchaseToken) {
        String hash = tokenCipher.hash(purchaseToken);
        subscriptionRepository.findByPurchaseTokenHash(hash).ifPresent(subscription -> {
            GooglePlaySubscriptionSnapshot snapshot = googlePlayApiClient
                    .getSubscription(purchaseToken);
            applySnapshot(subscription, snapshot);
            subscriptionRepository.save(subscription);
        });
    }

    private void applySnapshot(Subscription subscription,
                               GooglePlaySubscriptionSnapshot snapshot) {
        subscription.setProductId(snapshot.productId());
        subscription.setBasePlanId(snapshot.basePlanId());
        subscription.setStatus(mapStatus(snapshot));
        subscription.setStartedAt(snapshot.startedAt());
        subscription.setExpiresAt(snapshot.expiresAt());
        subscription.setAutoRenewing(snapshot.autoRenewing());
        subscription.setAcknowledged(snapshot.acknowledged());
        subscription.setLastVerifiedAt(Instant.now());
    }

    private SubscriptionStatus mapStatus(GooglePlaySubscriptionSnapshot snapshot) {
        return switch (snapshot.subscriptionState()) {
            case "SUBSCRIPTION_STATE_ACTIVE" -> SubscriptionStatus.PRO_ACTIVE;
            case "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" ->
                    SubscriptionStatus.PRO_GRACE_PERIOD;
            case "SUBSCRIPTION_STATE_CANCELED" -> snapshot.expiresAt() != null
                    && snapshot.expiresAt().isAfter(Instant.now())
                    ? SubscriptionStatus.PRO_CANCELED_BUT_ACTIVE
                    : SubscriptionStatus.PRO_EXPIRED;
            case "SUBSCRIPTION_STATE_PENDING" -> SubscriptionStatus.PENDING;
            default -> SubscriptionStatus.PRO_EXPIRED;
        };
    }

    private SubscriptionStatus effectiveStatus(Subscription subscription) {
        if (grantsPro(subscription.getStatus()) && subscription.getExpiresAt() != null
                && subscription.getExpiresAt().isBefore(Instant.now())) {
            return SubscriptionStatus.PRO_EXPIRED;
        }
        return subscription.getStatus();
    }

    private boolean grantsPro(SubscriptionStatus status) {
        return status == SubscriptionStatus.PRO_ACTIVE
                || status == SubscriptionStatus.PRO_GRACE_PERIOD
                || status == SubscriptionStatus.PRO_CANCELED_BUT_ACTIVE;
    }

    private void validateProduct(String requestedProductId, String verifiedProductId) {
        if (!verifiedProductId.equals(requestedProductId)
                || !properties.getProductIds().contains(verifiedProductId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Google Play product is not configured for Pro");
        }
    }

    private Subscription requireOwner(Subscription subscription, Long userId) {
        if (!subscription.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Purchase is linked to another account");
        }
        return subscription;
    }

    private int parseUsage(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}
