package com.olehprukhnytskyi.macrotrackeruserservice.properties;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "web-auth")
public class WebAuthProperties {
    private Set<String> allowedOrigins = Set.of("https://macrotracker.uk");
    private String googleClientId = "";
    private String appleServiceId = "";
    private String appleRedirectUri = "";
}
