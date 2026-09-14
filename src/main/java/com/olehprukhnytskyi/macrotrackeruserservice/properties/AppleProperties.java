package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import jakarta.validation.constraints.NotEmpty;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "social.apple")
public class AppleProperties {
    public static final String DEFAULT_JWK_SET_URI =
            "https://appleid.apple.com/auth/keys";

    @NotEmpty
    private Set<String> clientIds = new HashSet<>();
    private String jwkSetUri = DEFAULT_JWK_SET_URI;
}
