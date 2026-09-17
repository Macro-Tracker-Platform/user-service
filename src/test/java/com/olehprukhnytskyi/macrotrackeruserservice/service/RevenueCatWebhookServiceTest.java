package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserEntitlement;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.RevenueCatEventRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserEntitlementRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RevenueCatWebhookServiceTest {
    private static final Long USER_ID = 42L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserEntitlementRepository entitlementRepository;
    @Mock
    private RevenueCatEventRepository eventRepository;

    @Mock
    private PromoCodeService promoCodeService;

    private RevenueCatWebhookService webhookService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        RevenueCatProperties properties = new RevenueCatProperties();
        properties.setWebhookAuthorization("Bearer test-secret");
        webhookService = new RevenueCatWebhookService(
                properties, userRepository, entitlementRepository, eventRepository,
                promoCodeService);
        objectMapper = new ObjectMapper();
    }

    @Test
    void rejectsWrongAuthorization() {
        assertThatThrownBy(() -> webhookService.verifyAuthorization("Bearer wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("401 UNAUTHORIZED");
    }

    @Test
    void acceptsBearerAuthorizationWhenConfiguredValueIsTokenOnly() {
        RevenueCatProperties properties = new RevenueCatProperties();
        properties.setWebhookAuthorization("test-secret");
        RevenueCatWebhookService tokenOnlyWebhookService = new RevenueCatWebhookService(
                properties, userRepository, entitlementRepository, eventRepository,
                promoCodeService);

        tokenOnlyWebhookService.verifyAuthorization("Bearer test-secret");
    }

    @Test
    void initialPurchaseSubscribesUser() throws Exception {
        User user = new User();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        webhookService.process(payload("event-1", "INITIAL_PURCHASE", 1000L));

        var captor = org.mockito.ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlementRepository).save(captor.capture());
        assertThat(captor.getValue().isSubscribed()).isTrue();
        assertThat(captor.getValue().getSubscriptionEventTimestampMs()).isEqualTo(1000L);
        verify(eventRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void appStorePromoPurchaseIsAttributedByProductAndPurchaseTime() throws Exception {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(new User()));
        String json = """
                {"api_version":"1.0","event":{"id":"promo-1","type":"INITIAL_PURCHASE",
                "app_user_id":"42","event_timestamp_ms":2000,"purchased_at_ms":1500,
                "store":"APP_STORE","product_id":"yearly_promo_15","period_type":"INTRO"}}
                """;

        webhookService.process(objectMapper.readValue(json, RevenueCatWebhookDto.class));

        verify(promoCodeService).attributeApplePurchase(USER_ID, "yearly_promo_15",
                java.time.Instant.ofEpochMilli(1500));
    }

    @Test
    void fullPricePurchaseDoesNotConsumePromoClaim() throws Exception {
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(new User()));
        String json = """
                {"api_version":"1.0","event":{"id":"regular-1","type":"INITIAL_PURCHASE",
                "app_user_id":"42","event_timestamp_ms":2000,"purchased_at_ms":1500,
                "store":"APP_STORE","product_id":"yearly_promo_15","period_type":"NORMAL"}}
                """;

        webhookService.process(objectMapper.readValue(json, RevenueCatWebhookDto.class));

        verify(promoCodeService, never()).attributeApplePurchase(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void expirationUnsubscribesUser() throws Exception {
        User user = new User();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        UserEntitlement entitlement = UserEntitlement.builder()
                .userId(USER_ID)
                .subscribed(true)
                .subscriptionEventTimestampMs(1000L)
                .build();
        when(entitlementRepository.findById(USER_ID)).thenReturn(Optional.of(entitlement));

        webhookService.process(payload("event-2", "EXPIRATION", 2000L));

        assertThat(entitlement.isSubscribed()).isFalse();
    }

    @Test
    void ignoresDuplicateAndOutOfOrderEvents() throws Exception {
        User user = new User();
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));
        UserEntitlement entitlement = UserEntitlement.builder()
                .userId(USER_ID)
                .subscribed(true)
                .subscriptionEventTimestampMs(2000L)
                .build();
        when(entitlementRepository.findById(USER_ID)).thenReturn(Optional.of(entitlement));
        when(eventRepository.existsById("duplicate")).thenReturn(true);

        webhookService.process(payload("duplicate", "EXPIRATION", 3000L));
        webhookService.process(payload("old", "EXPIRATION", 1000L));

        assertThat(entitlement.isSubscribed()).isTrue();
        verify(entitlementRepository, never()).save(any());
    }

    @Test
    void transferMovesEntitlementToNewBackendUser() throws Exception {
        when(userRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        when(userRepository.findByIdForUpdate(43L)).thenReturn(Optional.of(new User()));
        UserEntitlement oldEntitlement = UserEntitlement.builder()
                .userId(42L).subscribed(true).subscriptionEventTimestampMs(1000L)
                .build();
        when(entitlementRepository.findById(42L)).thenReturn(Optional.of(oldEntitlement));
        String json = """
                {"api_version":"1.0","event":{"id":"transfer-1","type":"TRANSFER",
                "event_timestamp_ms":2000,"transferred_from":["42"],
                "transferred_to":["43"]}}
                """;

        webhookService.process(objectMapper.readValue(json, RevenueCatWebhookDto.class));

        assertThat(oldEntitlement.isSubscribed()).isFalse();
        var captor = org.mockito.ArgumentCaptor.forClass(UserEntitlement.class);
        verify(entitlementRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getUserId()).isEqualTo(43L);
        assertThat(captor.getAllValues().get(1).isSubscribed()).isTrue();
    }

    @Test
    void cancellationDoesNotRevokeAccess() throws Exception {
        webhookService.process(payload("event-3", "CANCELLATION", 3000L));

        verify(userRepository, never()).findByIdForUpdate(USER_ID);
    }

    private RevenueCatWebhookDto payload(String id, String type, long timestamp)
            throws Exception {
        String json = """
                {"api_version":"1.0","event":{"id":"%s","type":"%s",
                "app_user_id":"42","event_timestamp_ms":%d}}
                """.formatted(id, type, timestamp);
        return objectMapper.readValue(json, RevenueCatWebhookDto.class);
    }
}
