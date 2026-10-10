package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.time.Instant;

public record WebSubscriptionEligibilityDto(
        boolean checkoutEligible,
        boolean trialEligible,
        boolean reconciliationRequired,
        String reason,
        Instant currentAccessUntil
) {
}
