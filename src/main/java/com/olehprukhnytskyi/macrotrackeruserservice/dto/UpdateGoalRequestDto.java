package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateGoalRequestDto {
    @Min(value = 1, message = "Calories must be a positive number")
    private Integer calories;

    @Min(value = 0, message = "Protein cannot be negative")
    private Integer protein;

    @Min(value = 0, message = "Fat cannot be negative")
    private Integer fat;

    @Min(value = 0, message = "Carbohydrates cannot be negative")
    private Integer carbohydrates;
}
