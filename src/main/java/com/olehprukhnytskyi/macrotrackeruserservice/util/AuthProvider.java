package com.olehprukhnytskyi.macrotrackeruserservice.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.swagger.v3.oas.annotations.media.Schema;

public enum AuthProvider {
    @Schema(description = "Email & Password Sign in")
    LOCAL,

    @Schema(description = "Google OAuth2")
    GOOGLE,

    @Schema(description = "Facebook OAuth2")
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
