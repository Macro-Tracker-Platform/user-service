package com.olehprukhnytskyi.macrotrackeruserservice.dto;

public record WebAccountDto(String email, EntitlementResponseDto entitlement,
                            boolean webSubscription, WebCheckoutResponseDto pendingCheckout,
                            boolean campaignAdmin) {
}
