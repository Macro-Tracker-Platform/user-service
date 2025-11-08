package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.AuthProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Schema(description = "Social authentication request")
public class SocialTokenRequestDto {
    @Schema(
            description = "Social provider access token",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @NotBlank
    private String token;

    @Schema(
            description = "Social authentication provider",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    private AuthProvider provider;

    @Schema(
            description = "User profile details",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    private UserDetailsRequestDto userDetails;

    public SocialTokenRequestDto(String token, AuthProvider provider) {
        this.token = token;
        this.provider = provider;
    }
}
