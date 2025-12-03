package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for obtaining a new access token using a refresh token")
public class RefreshRequestDto {
    @NotBlank
    @Schema(
            description = "Valid refresh token",
            example = "eyJhbGciOiJSUzI1NiIsImtpZCI6IjhmOWExYzQxLT..."
    )
    private String refreshToken;
}
