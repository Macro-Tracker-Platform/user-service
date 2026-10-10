package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.RevenueCatSubscription;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.RevenueCatSubscriptionRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RevenueCatSubscriptionService {
    private static final Set<String> REVOKED = Set.of("EXPIRATION", "REVOCATION");
    private final RevenueCatSubscriptionRepository repository;

    public void record(Long userId, RevenueCatWebhookDto.Event event) {
        if (isBlank(event.getStore()) || isBlank(event.getOriginalTransactionId())
                || isBlank(event.getProductId()) || event.getExpirationAtMs() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Subscription events require store, transaction, product and expiration");
        }
        String id = subscriptionId(event.getStore(), event.getOriginalTransactionId());
        RevenueCatSubscription subscription = repository.findById(id)
                .orElseGet(() -> RevenueCatSubscription.builder().id(id).build());
        if (subscription.getEventTimestampMs() != null
                && event.getEventTimestampMs() <= subscription.getEventTimestampMs()) {
            return;
        }
        if (subscription.getUserId() != null && !userId.equals(subscription.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Subscription ownership must be reconciled through RevenueCat transfer");
        }
        long expiration = event.getExpirationAtMs();
        if (event.getGracePeriodExpirationAtMs() != null) {
            expiration = Math.max(expiration, event.getGracePeriodExpirationAtMs());
        }
        subscription.setUserId(userId);
        subscription.setStore(event.getStore());
        subscription.setOriginalTransactionId(event.getOriginalTransactionId());
        subscription.setProductId(event.getProductId());
        subscription.setPeriodType(event.getPeriodType());
        subscription.setExpiresAt(Instant.ofEpochMilli(expiration));
        subscription.setActive(!REVOKED.contains(event.getType()));
        subscription.setEventTimestampMs(event.getEventTimestampMs());
        repository.save(subscription);
    }

    public List<RevenueCatSubscription> subscriptions(Long userId) {
        return repository.findByUserId(userId);
    }

    public void transfer(Long fromUserId, Long toUserId, Long timestamp) {
        for (RevenueCatSubscription subscription : repository.findByUserId(fromUserId)) {
            if (subscription.getEventTimestampMs() < timestamp) {
                subscription.setUserId(toUserId);
                subscription.setEventTimestampMs(timestamp);
                repository.save(subscription);
            }
        }
    }

    public void detach(Long userId, Long timestamp) {
        for (RevenueCatSubscription subscription : repository.findByUserId(userId)) {
            if (subscription.getEventTimestampMs() < timestamp) {
                subscription.setActive(false);
                subscription.setEventTimestampMs(timestamp);
                repository.save(subscription);
            }
        }
    }

    private String subscriptionId(String store, String originalTransactionId) {
        try {
            byte[] value = (store + ":" + originalTransactionId)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
