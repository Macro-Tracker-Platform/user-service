package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.time.Instant;

public record WebCampaignResponseDto(String code, int discountPercent, Long affiliateId,
                                     boolean active, Instant validFrom, Instant validUntil,
                                     Integer maxRedemptions, boolean affiliateMigrationRequired) {
}
