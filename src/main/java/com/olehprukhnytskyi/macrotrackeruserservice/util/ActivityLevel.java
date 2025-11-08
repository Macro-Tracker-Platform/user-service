package com.olehprukhnytskyi.macrotrackeruserservice.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User activity level")
public enum ActivityLevel {
    @Schema(description = "Sedentary lifestyle")
    SEDENTARY,

    @Schema(description = "Light exercise 1-3 days/week")
    LIGHTLY_ACTIVE,

    @Schema(description = "Moderate exercise 3-5 days/week")
    MODERATELY_ACTIVE,

    @Schema(description = "Hard exercise 6-7 days/week")
    VERY_ACTIVE,

    @Schema(description = "Very hard exercise/physical job")
    EXTRA_ACTIVE;

    @JsonCreator
    public static ActivityLevel fromString(String value) {
        return ActivityLevel.valueOf(value.toUpperCase());
    }
}
