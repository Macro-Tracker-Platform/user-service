package com.olehprukhnytskyi.macrotrackeruserservice.util;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum AuthProvider {
    LOCAL,
    GOOGLE,
    FACEBOOK;

    @JsonCreator
    public static AuthProvider fromString(String value) {
        try {
            return AuthProvider.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown provider: " + value);
        }
    }
}
