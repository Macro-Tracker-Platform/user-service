package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SocialTokenRequestDto {
    @NotNull
    @NotBlank
    private String token;

    @NotNull
    @NotBlank
    private String provider;
}
