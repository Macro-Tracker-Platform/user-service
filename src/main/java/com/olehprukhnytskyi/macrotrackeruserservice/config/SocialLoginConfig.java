package com.olehprukhnytskyi.macrotrackeruserservice.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GoogleProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebAuthProperties;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class SocialLoginConfig {
    private final GoogleProperties googleProperties;
    private final WebAuthProperties webAuthProperties;

    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier() {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Stream.of(googleProperties.getClientId(),
                                webAuthProperties.getGoogleClientId())
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .toList())
                .build();
    }
}
