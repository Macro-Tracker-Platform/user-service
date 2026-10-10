package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.time.Instant;

public record WebCheckoutResponseDto(String id, String state, String url, Instant expiresAt) {
}
