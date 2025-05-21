package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.validation.PasswordMatches;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@PasswordMatches
@NoArgsConstructor
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

    @NotNull
    private UserDetailsRequestDto userDetails;

    public RegisterRequestDto(String email, String password, String confirmPassword) {
        this.email = email;
        this.password = password;
        this.confirmPassword = confirmPassword;
    }
}
