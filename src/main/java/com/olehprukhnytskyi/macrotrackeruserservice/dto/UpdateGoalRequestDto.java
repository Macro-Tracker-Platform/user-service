package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
@Schema(description = "Update user nutrition goals request")
public class UpdateGoalRequestDto {
    @Schema(description = "Daily calorie target", example = "2000", minimum = "1")
    @Min(value = 1, message = "Calories must be a positive number")
    private Integer calories;

    @Schema(description = "Daily protein target in grams", example = "150", minimum = "0")
    @Min(value = 0, message = "Protein cannot be negative")
    private Integer protein;

    @Schema(description = "Daily fat target in grams", example = "65", minimum = "0")
    @Min(value = 0, message = "Fat cannot be negative")
    private Integer fat;

    @Schema(description = "Daily carbohydrates target in grams", example = "250", minimum = "0")
    @Min(value = 0, message = "Carbohydrates cannot be negative")
    private Integer carbohydrates;

    @Schema(description = "Daily water target in milliliters", example = "2500", minimum = "1")
    @Min(value = 1, message = "Water goal must be a positive number")
    private Integer waterGoalMl;
}
