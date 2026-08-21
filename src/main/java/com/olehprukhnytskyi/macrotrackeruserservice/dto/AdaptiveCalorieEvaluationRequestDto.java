package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdaptiveCalorieEvaluationRequestDto {
    @Builder.Default
    private List<DailyCalorieSampleDto> summaries = List.of();
    @Builder.Default
    private List<WeightSampleDto> weights = List.of();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyCalorieSampleDto {
        private LocalDate date;
        private BigDecimal calories;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeightSampleDto {
        private LocalDate date;
        private BigDecimal weight;
    }
}
