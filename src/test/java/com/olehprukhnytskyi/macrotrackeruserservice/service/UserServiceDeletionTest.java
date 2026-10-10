package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserServiceDeletionTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WebCheckoutRepository checkouts = mock(WebCheckoutRepository.class);
    private final OutboxRepository outbox = mock(OutboxRepository.class);
    private final WebAccountDeletionService billing = mock(WebAccountDeletionService.class);
    private final UserService service = new UserService(users, outbox, checkouts, billing);

    @BeforeEach
    void setup() {
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
    }

    @Test
    void billingIsStoppedBeforeProfileDeletionAndFinancialJournalIsRetained() {
        var checkout = WebCheckout.builder().id("owned").checkoutUrl("private-url").build();
        when(checkouts.findByUserIdOrderByIdAsc(42L)).thenReturn(List.of(checkout));
        when(checkouts.findByIdForUpdate("owned")).thenReturn(Optional.of(checkout));
        service.deleteById(42L);
        var order = inOrder(billing, users, outbox);
        order.verify(billing).prepare(42L);
        order.verify(users).deleteById(42L);
        order.verify(outbox).save(any());
        assertThat(checkout.getAccountDeletedAt()).isNotNull();
        assertThat(checkout.getCheckoutUrl()).isNull();
        verify(checkouts, never()).delete(any());
    }

    @Test
    void retryAfterLostDeletionResponseDoesNotPublishOrCancelAgain() {
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.empty());
        service.deleteById(42L);
        org.mockito.Mockito.verifyNoInteractions(billing, checkouts, outbox);
    }

    @Test
    void providerErrorReturnsDeletionSpecificMessageWithoutRemovingAccount() {
        doThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE))
                .when(billing).prepare(42L);
        assertThatThrownBy(() -> service.deleteById(42L))
                .hasMessageContaining("ACCOUNT_DELETION_BILLING_UNCONFIRMED");
        verify(users, never()).deleteById(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void unconfirmedCancellationKeepsAccountAndDoesNotPublishDeletion() {
        doThrow(new IllegalStateException("provider unavailable")).when(billing).prepare(42L);
        assertThatThrownBy(() -> service.deleteById(42L))
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).deleteById(any());
        verify(outbox, never()).save(any());
    }
}
