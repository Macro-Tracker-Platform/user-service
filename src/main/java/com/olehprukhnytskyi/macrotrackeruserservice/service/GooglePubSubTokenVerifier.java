package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class GooglePubSubTokenVerifier {
    private final GooglePlayProperties properties;
    private final GoogleIdTokenVerifier verifier;

    public GooglePubSubTokenVerifier(GooglePlayProperties properties) {
        this.properties = properties;
        this.verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(), new GsonFactory())
                .build();
    }

    public void verify(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized();
        }
        String audience = properties.getRtdnAudience();
        String expectedEmail = properties.getRtdnServiceAccountEmail();
        if (audience == null || audience.isBlank()
                || expectedEmail == null || expectedEmail.isBlank()) {
            throw new IllegalStateException("Google Pub/Sub push authentication is not configured");
        }
        try {
            GoogleIdToken token = verifier.verify(authorization.substring("Bearer ".length()));
            if (token == null
                    || !token.verifyAudience(List.of(audience))
                    || !expectedEmail.equals(token.getPayload().getEmail())
                    || !Boolean.TRUE.equals(token.getPayload().getEmailVerified())) {
                unauthorized();
            }
        } catch (GeneralSecurityException | IOException exception) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid Pub/Sub identity token", exception);
        }
    }

    private void unauthorized() {
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid Pub/Sub identity token");
    }
}
