package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.nimbusds.jwt.SignedJWT;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebAuthProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GoogleWebOAuthService {
    private final WebAuthProperties properties;
    private final RestClient client;

    public GoogleWebOAuthService(WebAuthProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = builder.requestFactory(factory).build();
    }

    public boolean configured() {
        return !properties.getGoogleClientId().isBlank()
                && !properties.getGoogleClientSecret().isBlank();
    }

    public String exchange(String code, String origin) {
        if (!configured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "GOOGLE_WEB_LOGIN_NOT_CONFIGURED");
        }
        // Only exact allowed origins can be used as the GIS popup redirect URI.
        if (!properties.getAllowedOrigins().contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "WEB_ORIGIN_NOT_ALLOWED");
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("code", code);
        form.add("client_id", properties.getGoogleClientId());
        form.add("client_secret", properties.getGoogleClientSecret());
        form.add("redirect_uri", origin);
        form.add("grant_type", "authorization_code");
        try {
            var result = client.post().uri("https://oauth2.googleapis.com/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(TokenResponse.class);
            if (result == null || result.id_token() == null
                    || !SignedJWT.parse(result.id_token()).getJWTClaimsSet().getAudience()
                            .contains(properties.getGoogleClientId())) {
                throw new IllegalArgumentException("Invalid Google web audience");
            }
            // AuthService still verifies signature, issuer, expiry, and existing account.
            return result.id_token();
        } catch (Exception exception) {
            // Never log authorization codes, secrets, or Google's token response.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SOCIAL_LOGIN_INVALID");
        }
    }

    private record TokenResponse(String id_token) {
    }
}
