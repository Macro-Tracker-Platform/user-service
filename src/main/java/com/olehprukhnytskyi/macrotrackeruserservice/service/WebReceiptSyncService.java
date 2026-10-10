package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebReceiptSyncService {
    private final WebBillingProperties properties;
    private final WebCheckoutRepository repository;
    private final WebReceiptImportService receipts;

    @Scheduled(fixedDelayString = "${web-billing.receipt-sync-delay-ms:30000}")
    public void syncPending() {
        if (!properties.isEnabled()) {
            return;
        }
        for (WebCheckout checkout : repository
                .pendingReceiptSync(WebCheckoutState.COMPLETED,
                        org.springframework.data.domain.PageRequest.of(0, 20))) {
            try {
                receipts.sync(checkout.getId());
            } catch (RuntimeException exception) {
                repository.deferReceiptSync(checkout.getId(), checkout.getReceiptSyncRequestedAt(),
                        Instant.now());
                log.warn("RevenueCat receipt sync pending for checkout {}", checkout.getId());
            }
        }
    }
}
