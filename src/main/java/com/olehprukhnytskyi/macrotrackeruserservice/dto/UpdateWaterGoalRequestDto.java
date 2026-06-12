package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "Update custom daily water goal request")
public class UpdateWaterGoalRequestDto {
    @NotNull
    @Min(value = 250, message = "Water goal must be at least 250 ml")
    @Max(value = 10000, message = "Water goal must not exceed 10000 ml")
    @Schema(description = "Custom daily water target in milliliters", example = "2700")
    private Integer waterGoalMl;
}
