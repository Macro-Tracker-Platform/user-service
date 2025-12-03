package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Authentication response containing access and refresh tokens")
public class AuthResponseDto {
    @Schema(
            description = "JWT access token used for authenticated API requests",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjhmOWExYzQxLT..."
    )
    private String accessToken;

    @Schema(
            description = "JWT refresh token used to obtain a new access token",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjhmOWExYzQxLT..."
    )
    private String refreshToken;
}
