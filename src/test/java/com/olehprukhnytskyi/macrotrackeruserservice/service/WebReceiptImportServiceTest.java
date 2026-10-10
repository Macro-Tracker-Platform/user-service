package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WebReceiptImportServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WebCheckoutRepository checkouts = mock(WebCheckoutRepository.class);
    private final RevenueCatApiClient revenueCat = mock(RevenueCatApiClient.class);
    private final WebReceiptImportService service =
            new WebReceiptImportService(users, checkouts, revenueCat);
    private final WebCheckout checkout = WebCheckout.builder().id("attempt").userId(42L)
            .state(WebCheckoutState.COMPLETED).stripeSubscriptionId("sub_owned").build();

    @Test
    void alreadyPickedImportDoesNotRecreateDeletedCustomer() {
        when(checkouts.findUserIdById("attempt")).thenReturn(Optional.of(42L));
        service.sync("attempt");
        verifyNoInteractions(revenueCat);
    }

    @Test
    void tombstoneAlsoPreventsReceiptEvenIfAccountRowStillExists() {
        setupOwner();
        checkout.setAccountDeletedAt(Instant.now());
        service.sync("attempt");
        verifyNoInteractions(revenueCat);
    }

    @Test
    void ownedReceiptIsImportedAndAcknowledgedUnderSameLock() {
        setupOwner();
        service.sync("attempt");
        verify(revenueCat).importStripeSubscription(42L, "sub_owned");
        assertThat(checkout.getReceiptSyncedAt()).isNotNull();
    }

    private void setupOwner() {
        when(checkouts.findUserIdById("attempt")).thenReturn(Optional.of(42L));
        when(users.findByIdForUpdate(42L)).thenReturn(Optional.of(new User()));
        when(checkouts.findByIdForUpdate("attempt")).thenReturn(Optional.of(checkout));
    }
}
