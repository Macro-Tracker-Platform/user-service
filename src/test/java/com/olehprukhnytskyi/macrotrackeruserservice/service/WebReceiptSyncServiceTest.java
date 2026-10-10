package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebReceiptSyncServiceTest {
    private final WebBillingProperties properties = new WebBillingProperties();
    private final WebCheckoutRepository repository = mock(WebCheckoutRepository.class);
    private final WebReceiptImportService receipts = mock(WebReceiptImportService.class);
    private final WebReceiptSyncService service =
            new WebReceiptSyncService(properties, repository, receipts);

    @Test
    void disabledBillingDoesNotSendReceipts() {
        service.syncPending();
        verifyNoInteractions(repository, receipts);
    }

    @Test
    void oneFailedReceiptDoesNotStopOthersAndNewerRequestsAreProtected() {
        properties.setEnabled(true);
        Instant version = Instant.parse("2026-01-01T00:00:00Z");
        WebCheckout failed = checkout("failed", "sub_failed", version);
        WebCheckout success = checkout("success", "sub_success", version);
        when(repository.pendingReceiptSync(any(), any())).thenReturn(List.of(failed, success));
        doThrow(new IllegalStateException("test failure"))
                .when(receipts).sync("failed");
        service.syncPending();
        verify(repository).deferReceiptSync(eq("failed"), eq(version), any());
        verify(receipts).sync("success");
    }

    private WebCheckout checkout(String id, String subscription, Instant version) {
        return WebCheckout.builder().id(id).userId(42L).stripeSubscriptionId(subscription)
                .receiptSyncRequestedAt(version).build();
    }
}
