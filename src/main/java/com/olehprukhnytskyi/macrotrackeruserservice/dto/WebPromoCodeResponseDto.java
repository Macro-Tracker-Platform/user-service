package com.olehprukhnytskyi.macrotrackeruserservice.dto;

public record WebPromoCodeResponseDto(
        String code,
        int discountPercent,
        WebSubscriptionEligibilityDto eligibility
) {
}
