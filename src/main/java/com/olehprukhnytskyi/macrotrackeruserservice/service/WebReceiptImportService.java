package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WebReceiptImportService {
    private final UserRepository users;
    private final WebCheckoutRepository checkouts;
    private final RevenueCatApiClient revenueCat;

    @Transactional
    public void sync(String id) {
        Long userId = checkouts.findUserIdById(id).orElseThrow();
        // Serialize imports with deletion, including work already picked by the scheduled job.
        if (users.findByIdForUpdate(userId).isEmpty()) {
            return;
        }
        var checkout = checkouts.findByIdForUpdate(id).orElseThrow();
        if (checkout.getAccountDeletedAt() != null || checkout.getReceiptSyncedAt() != null
                || checkout.getState() != WebCheckoutState.COMPLETED) {
            return;
        }
        revenueCat.importStripeSubscription(checkout.getUserId(),
                checkout.getStripeSubscriptionId());
        checkout.setReceiptSyncedAt(Instant.now());
    }
}
