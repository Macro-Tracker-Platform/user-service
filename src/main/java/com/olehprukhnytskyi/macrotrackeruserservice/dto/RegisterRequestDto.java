package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@PasswordMatches
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequestDto {
    @Email
    @NotNull
    @Size(max = 320)
    private String email;

    @NotNull
    @Size(min = 8, max = 64)
    private String password;

    @NotNull
    @Size(min = 8, max = 64)
    private String confirmPassword;
}
