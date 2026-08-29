package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.RevenueCatEvent;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserEntitlement;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.RevenueCatEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserEntitlementRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RevenueCatWebhookService {
    private static final Set<String> ACTIVATE_EVENTS = Set.of(
            "INITIAL_PURCHASE", "RENEWAL");
    private static final Set<String> DEACTIVATE_EVENTS = Set.of(
            "EXPIRATION", "REVOCATION");

    private final RevenueCatProperties properties;
    private final UserRepository userRepository;
    private final UserEntitlementRepository entitlementRepository;
    private final RevenueCatEventRepository eventRepository;

    public void verifyAuthorization(String authorization) {
        String expected = properties.getWebhookAuthorization();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "RevenueCat webhook authorization is not configured");
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = authorization == null
                ? new byte[0] : authorization.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid RevenueCat webhook authorization");
        }
    }

    @Transactional
    public void process(RevenueCatWebhookDto payload) {
        RevenueCatWebhookDto.Event event = payload == null ? null : payload.getEvent();
        if (event == null || event.getId() == null || event.getId().isBlank()
                || event.getType() == null || event.getType().isBlank()
                || event.getEventTimestampMs() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid RevenueCat webhook payload");
        }

        Boolean subscribed = subscriptionState(event.getType());
        if (subscribed == null) {
            return;
        }
        Long userId = parseUserId(event.getAppUserId());
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "RevenueCat App User ID does not match a backend user"));
        if (eventRepository.existsById(event.getId())) {
            return;
        }

        UserEntitlement entitlement = entitlementRepository.findById(userId)
                .orElse(null);
        Long lastTimestamp = entitlement == null
                ? null : entitlement.getSubscriptionEventTimestampMs();
        if (lastTimestamp == null || event.getEventTimestampMs() >= lastTimestamp) {
            if (entitlement == null) {
                entitlement = UserEntitlement.builder().userId(userId).build();
            }
            entitlement.setSubscribed(subscribed);
            entitlement.setSubscriptionEventTimestampMs(event.getEventTimestampMs());
            entitlementRepository.save(entitlement);
        }
        eventRepository.save(RevenueCatEvent.builder()
                .id(event.getId())
                .eventType(event.getType())
                .appUserId(event.getAppUserId())
                .eventTimestampMs(event.getEventTimestampMs())
                .processedAt(Instant.now())
                .build());
    }

    private Boolean subscriptionState(String eventType) {
        if (ACTIVATE_EVENTS.contains(eventType)) {
            return true;
        }
        if (DEACTIVATE_EVENTS.contains(eventType)) {
            return false;
        }
        return null;
    }

    private Long parseUserId(String appUserId) {
        try {
            return Long.valueOf(appUserId);
        } catch (NumberFormatException | NullPointerException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "RevenueCat App User ID must be a backend user ID");
        }
    }
}
