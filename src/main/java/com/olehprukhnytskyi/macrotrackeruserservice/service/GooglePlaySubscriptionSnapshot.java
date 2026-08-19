package com.olehprukhnytskyi.macrotrackeruserservice.service;

import java.time.Instant;

public record GooglePlaySubscriptionSnapshot(
        String productId,
        String basePlanId,
        String subscriptionState,
        Instant startedAt,
        Instant expiresAt,
        boolean autoRenewing,
        boolean acknowledged) {
}
