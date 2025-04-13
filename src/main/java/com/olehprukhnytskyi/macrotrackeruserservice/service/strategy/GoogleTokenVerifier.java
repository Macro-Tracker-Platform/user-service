package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleTokenVerifier implements SocialTokenVerifier {
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    @Override
    public boolean supports(String provider) {
        return "google".equalsIgnoreCase(provider);
    }

    @Override
    public SocialUserPayload verify(String token) {
        GoogleIdToken idToken;
        try {
            idToken = googleIdTokenVerifier.verify(token);
        } catch (Exception e) {
            throw new TokenVerificationException("Unable to verify Google token", e);
        }
        if (idToken != null && idToken.getPayload() != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();
            return new SocialUserPayload(payload.getEmail());
        }
        throw new TokenVerificationException("Google token is invalid or malformed");
    }
}
