package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.service.strategy.SocialTokenVerifier;
import com.olehprukhnytskyi.macrotrackeruserservice.util.AuthProvider;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SocialTokenVerificationServiceTest {
    @Mock
    private SocialTokenVerifier googleTokenVerifier;
    @Mock
    private SocialTokenVerifier facebookTokenVerifier;

    private SocialTokenVerificationService socialTokenVerificationService;

    @BeforeEach
    void setUp() {
        socialTokenVerificationService = new SocialTokenVerificationService(
                List.of(googleTokenVerifier, facebookTokenVerifier)
        );
    }

    @Test
    @DisplayName("Given valid token, should return valid user payload")
    void verifyToken_whenValidProvider_shouldReturnPayload() {
        // Given
        SocialUserDetails expectedPayload = new SocialUserDetails("test@example.com");

        when(googleTokenVerifier.supports(AuthProvider.GOOGLE)).thenReturn(true);
        when(googleTokenVerifier.verify("test_token")).thenReturn(expectedPayload);

        // When
        SocialUserDetails result = socialTokenVerificationService
                .verifyToken("test_token", AuthProvider.GOOGLE);

        // Then
        assertEquals(expectedPayload, result);
        verify(googleTokenVerifier).verify("test_token");
        verifyNoInteractions(facebookTokenVerifier);
    }

    @Test
    @DisplayName("Given unsupported provider, should throw an exception")
    void verifyToken_whenUnsupportedProvider_shouldThrowException() {
        // Given
        when(googleTokenVerifier.supports(AuthProvider.GOOGLE)).thenReturn(false);
        when(facebookTokenVerifier.supports(AuthProvider.GOOGLE)).thenReturn(false);

        // When
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> socialTokenVerificationService.verifyToken("test_token", AuthProvider.GOOGLE)
        );

        // Then
        assertEquals("Unsupported provider: google", exception.getMessage());
    }
}
