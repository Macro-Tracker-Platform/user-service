package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import com.olehprukhnytskyi.macrotrackeruserservice.util.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GoogleTokenVerifier implements SocialTokenVerifier {
    private final GoogleIdTokenVerifier googleIdTokenVerifier;

    @Override
    public boolean supports(AuthProvider provider) {
        return provider == AuthProvider.GOOGLE;
    }

    @Override
    public SocialUserDetails verify(String token) {
        GoogleIdToken googleIdToken;
        try {
            googleIdToken = googleIdTokenVerifier.verify(token);
        } catch (Exception e) {
            throw new TokenVerificationException("Unable to verify Google token", e);
        }
        if (googleIdToken != null && googleIdToken.getPayload() != null) {
            GoogleIdToken.Payload payload = googleIdToken.getPayload();
            return new SocialUserDetails(payload.getEmail());
        }
        throw new TokenVerificationException("Google token is invalid or malformed");
    }
}
