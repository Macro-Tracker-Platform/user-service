package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import com.olehprukhnytskyi.macrotrackeruserservice.util.AuthProvider;
import java.io.IOException;
import java.security.GeneralSecurityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoogleTokenVerifierTest {
    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;
    @Mock
    private GoogleIdToken googleIdToken;
    @Mock
    private GoogleIdToken.Payload payload;

    @InjectMocks
    private GoogleTokenVerifier googleTokenVerifier;

    @Test
    @DisplayName("Given a valid token, should return a valid user payload")
    void verify_whenValidToken_shouldReturnValidUserPayload() throws Exception {
        // Given
        String token = "valid_token";

        when(googleIdTokenVerifier.verify(token)).thenReturn(googleIdToken);
        when(googleIdToken.getPayload()).thenReturn(payload);
        when(payload.getEmail()).thenReturn("test@example.com");

        // When
        SocialUserDetails userPayload = googleTokenVerifier.verify(token);

        // Then
        assertEquals("test@example.com", userPayload.getEmail());
    }

    @Test
    @DisplayName("Given a null or blank token, should throw an exception")
    void verify_whenNullOrBlankToken_shouldThrowException()
            throws GeneralSecurityException, IOException {
        // Given
        when(googleIdTokenVerifier.verify(googleIdToken))
                .thenThrow(GeneralSecurityException.class);

        // When
        TokenVerificationException exceptionNull = assertThrows(
                TokenVerificationException.class,
                () -> googleTokenVerifier.verify(null)
        );
        TokenVerificationException exceptionBlank = assertThrows(
                TokenVerificationException.class,
                () -> googleTokenVerifier.verify("")
        );

        // Then
        String expected = "Unable to verify Google token";
        assertEquals(expected, exceptionNull.getMessage());
        assertEquals(expected, exceptionBlank.getMessage());
    }

    @Test
    @DisplayName("Given a malformed token, should throw an exception")
    void verify_whenMalformedToken_shouldThrowException() throws Exception {
        // Given
        when(googleIdTokenVerifier.verify(anyString())).thenReturn(null);

        // When
        TokenVerificationException exception = assertThrows(
                TokenVerificationException.class,
                () -> googleTokenVerifier.verify("malformed_token")
        );

        // Then
        String expected = "Google token is invalid or malformed";
        assertEquals(expected, exception.getMessage());
    }

    @Test
    @DisplayName("Should return true, when 'google' provider")
    void supports_whenGoogleProvider_shouldReturnTrue() {
        assertTrue(googleTokenVerifier.supports(AuthProvider.GOOGLE));
    }

    @Test
    @DisplayName("Should return false, when non-google provider")
    void supports_whenOtherProvider_shouldReturnFalse() {
        assertThrows(IllegalArgumentException.class, () -> googleTokenVerifier
                .supports(AuthProvider.fromString("non_existing_provider")));
    }
}
