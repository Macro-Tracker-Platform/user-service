package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "social.facebook")
public class FacebookProperties {
    @NotBlank
    private String appId;

    @NotBlank
    private String appSecret;
}
