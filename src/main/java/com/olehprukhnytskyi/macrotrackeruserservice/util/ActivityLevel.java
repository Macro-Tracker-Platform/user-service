package com.olehprukhnytskyi.macrotrackeruserservice.util;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ActivityLevel {
    SEDENTARY,
    LIGHTLY_ACTIVE,
    MODERATELY_ACTIVE,
    VERY_ACTIVE,
    EXTRA_ACTIVE;

    @JsonCreator
    public static ActivityLevel fromString(String value) {
        return ActivityLevel.valueOf(value.toUpperCase());
    }
}
