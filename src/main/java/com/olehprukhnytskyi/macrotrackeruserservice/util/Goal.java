package com.olehprukhnytskyi.macrotrackeruserservice.util;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Goal {
    LOSE,
    MAINTAIN,
    GAIN;

    @JsonCreator
    public static Goal fromString(String value) {
        return Goal.valueOf(value.toUpperCase());
    }
}
