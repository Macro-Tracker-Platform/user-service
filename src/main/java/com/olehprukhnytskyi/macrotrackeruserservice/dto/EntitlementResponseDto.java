package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementResponseDto {
    private String plan;
    private SubscriptionStatus state;
    private Instant validUntil;
    private boolean legacyAccess;
    private Features features;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Features {
        private ScanAllowance nutritionLabelScans;
        private boolean advancedInsights;
        private boolean futurePlanning;
        private boolean weekdayGoals;
        private boolean adaptiveCalories;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScanAllowance {
        private int limit;
        private int remaining;
        private Instant resetAt;
    }
}
