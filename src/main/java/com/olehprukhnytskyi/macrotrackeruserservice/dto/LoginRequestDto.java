package com.olehprukhnytskyi.macrotrackeruserservice.dto;

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
public class LoginRequestDto {
    @Email
    @NotNull
    @Length(max = 320)
    private String email;

    @NotNull
    @Size(min = 8, max = 64)
    private String password;
}
