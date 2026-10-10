package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.RevenueCatSubscription;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.RevenueCatSubscriptionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RevenueCatSubscriptionServiceTest {
    @Mock
    private RevenueCatSubscriptionRepository repository;
    @InjectMocks
    private RevenueCatSubscriptionService service;

    @Test
    void cancellationRetainsAccessOnlyUntilConfirmedExpiry() throws Exception {
        service.record(42L, event("CANCELLATION", 2000, 9000));

        ArgumentCaptor<RevenueCatSubscription> saved =
                ArgumentCaptor.forClass(RevenueCatSubscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(Instant.ofEpochMilli(9000));
        assertThat(saved.getValue().getId()).hasSize(64);
    }

    @Test
    void lateRenewalCannotReactivateRevokedTransaction() throws Exception {
        RevenueCatSubscription existing = RevenueCatSubscription.builder()
                .userId(42L).active(false).eventTimestampMs(3000L).build();
        when(repository.findById(any())).thenReturn(Optional.of(existing));

        service.record(42L, event("RENEWAL", 2000, 9000));

        assertThat(existing.isActive()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void expirationRevokesOnlyItsTransaction() throws Exception {
        service.record(42L, event("EXPIRATION", 3000, 9000));

        ArgumentCaptor<RevenueCatSubscription> saved =
                ArgumentCaptor.forClass(RevenueCatSubscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().isActive()).isFalse();
        assertThat(saved.getValue().getOriginalTransactionId()).isEqualTo("web-subscription");
    }

    @Test
    void purchaseWithoutExpirationCannotGrantUnboundedPremium() throws Exception {
        var event = new ObjectMapper().readValue("""
                {"event":{"type":"INITIAL_PURCHASE","store":"STRIPE",
                "original_transaction_id":"web-subscription","product_id":"yearly",
                "event_timestamp_ms":2000}}
                """, RevenueCatWebhookDto.class).getEvent();

        assertThatThrownBy(() -> service.record(42L, event))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("expiration");
        verify(repository, never()).save(any());
    }

    @Test
    void transferPreservesExpiryAndRevocation() {
        RevenueCatSubscription existing = RevenueCatSubscription.builder()
                .userId(42L).active(false).eventTimestampMs(2000L)
                .expiresAt(Instant.ofEpochMilli(9000)).build();
        when(repository.findByUserId(42L)).thenReturn(List.of(existing));

        service.transfer(42L, 43L, 3000L);

        assertThat(existing.getUserId()).isEqualTo(43L);
        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getExpiresAt()).isEqualTo(Instant.ofEpochMilli(9000));
    }

    @Test
    void transferToAnonymousIdentityDetachesBackendAccess() {
        RevenueCatSubscription existing = RevenueCatSubscription.builder()
                .userId(42L).active(true).eventTimestampMs(2000L).build();
        when(repository.findByUserId(42L)).thenReturn(List.of(existing));

        service.detach(42L, 3000L);

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getUserId()).isEqualTo(42L);
    }

    @Test
    void billingIssueRetainsConfirmedGracePeriod() throws Exception {
        String json = """
                {"event":{"type":"BILLING_ISSUE","store":"STRIPE",
                "original_transaction_id":"web-subscription","product_id":"yearly",
                "event_timestamp_ms":2000,"expiration_at_ms":3000,
                "grace_period_expiration_at_ms":9000}}
                """;
        service.record(42L, new ObjectMapper().readValue(json,
                RevenueCatWebhookDto.class).getEvent());

        ArgumentCaptor<RevenueCatSubscription> saved =
                ArgumentCaptor.forClass(RevenueCatSubscription.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(Instant.ofEpochMilli(9000));
    }

    @Test
    void staleTransferCannotMoveNewerOwnership() {
        RevenueCatSubscription existing = RevenueCatSubscription.builder()
                .userId(42L).active(true).eventTimestampMs(3000L).build();
        when(repository.findByUserId(42L)).thenReturn(List.of(existing));

        service.transfer(42L, 43L, 2000L);

        assertThat(existing.getUserId()).isEqualTo(42L);
        verify(repository, never()).save(any());
    }

    private RevenueCatWebhookDto.Event event(String type, long timestamp, long expiry)
            throws Exception {
        return new ObjectMapper().readValue("""
                {"event":{"type":"%s","store":"STRIPE",
                "original_transaction_id":"web-subscription","product_id":"yearly",
                "event_timestamp_ms":%d,"expiration_at_ms":%d}}
                """.formatted(type, timestamp, expiry), RevenueCatWebhookDto.class).getEvent();
    }
}
