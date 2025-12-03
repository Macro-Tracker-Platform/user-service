package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {
    @NotBlank
    private String keyId;

    @NotBlank
    private String publicKey;

    @NotBlank
    private String privateKey;

    @NotNull
    @Min(1)
    private Long accessTokenTtlMinutes;

    @NotNull
    @Min(1)
    private Long refreshTokenTtlDays;
}
