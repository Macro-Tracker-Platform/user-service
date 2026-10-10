package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;

public record WebCampaignRequestDto(@Min(1) @Max(99) int discountPercent,
                                    Long affiliateId, boolean active,
                                    Instant validFrom, Instant validUntil,
                                    @Min(1) Integer maxRedemptions) {
}
