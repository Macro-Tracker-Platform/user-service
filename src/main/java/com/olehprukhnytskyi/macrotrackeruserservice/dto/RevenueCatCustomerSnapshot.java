package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.time.Instant;

public record RevenueCatCustomerSnapshot(boolean active, boolean hasPurchaseHistory,
                                         Instant expiresAt, String store) {
    public RevenueCatCustomerSnapshot(boolean active, boolean hasPurchaseHistory,
                                      Instant expiresAt) {
        this(active, hasPurchaseHistory, expiresAt, null);
    }
}
