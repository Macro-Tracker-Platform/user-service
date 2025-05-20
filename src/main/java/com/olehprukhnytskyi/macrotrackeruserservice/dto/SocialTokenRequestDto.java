package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.AuthProvider;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class SocialTokenRequestDto {
    @NotNull
    @NotBlank
    private String token;

    @NotNull
    private AuthProvider provider;

    @NotNull
    private UserDetailsRequestDto userDetails;

    public SocialTokenRequestDto(String token, AuthProvider provider) {
        this.token = token;
        this.provider = provider;
    }
}
