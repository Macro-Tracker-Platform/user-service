package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.validator.constraints.Length;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User login credentials")
public class LoginRequestDto {
    @Schema(
            description = "User email address",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @Email
    @NotNull
    @Length(max = 320)
    private String email;

    @Schema(
            description = "User password",
            example = "password123",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull
    @Size(min = 8, max = 64)
    private String password;
}
