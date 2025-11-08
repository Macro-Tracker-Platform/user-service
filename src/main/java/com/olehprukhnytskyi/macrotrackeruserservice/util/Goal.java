package com.olehprukhnytskyi.macrotrackeruserservice.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "User fitness goals")
public enum Goal {
    @Schema(description = "Weight loss")
    LOSE,

    @Schema(description = "Weight maintenance")
    MAINTAIN,

    @Schema(description = "Muscle gain")
    GAIN;

    @JsonCreator
    public static Goal fromString(String value) {
        return Goal.valueOf(value.toUpperCase());
    }
}
