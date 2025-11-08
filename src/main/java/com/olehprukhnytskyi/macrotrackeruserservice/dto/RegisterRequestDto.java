package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.validation.PasswordMatches;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@PasswordMatches
@NoArgsConstructor
@Schema(description = "User registration data")
public class RegisterRequestDto {
    @Schema(
            description = "User email address",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @Email
    @NotNull
    @Size(max = 320)
    private String email;

    @Schema(
            description = "User password",
            example = "password123",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @Size(min = 8, max = 64)
    private String password;

    @Schema(
            description = "Password confirmation",
            example = "password123",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @Size(min = 8, max = 64)
    private String confirmPassword;

    @Schema(
            description = "User profile details",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    private UserDetailsRequestDto userDetails;

    public RegisterRequestDto(String email, String password, String confirmPassword) {
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }
}
