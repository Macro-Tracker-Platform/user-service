package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User nutrition goals")
public class GoalResponseDto {
    @Schema(description = "Daily calorie target", example = "2000")
    private int calories;

    @Schema(description = "Daily protein target in grams", example = "150")
    private int protein;

    @Schema(description = "Daily fat target in grams", example = "65")
    private int fat;

    @Schema(description = "Daily carbohydrates target in grams", example = "250")
    private int carbohydrates;
}
