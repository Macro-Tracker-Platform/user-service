package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
import com.olehprukhnytskyi.macrotrackeruserservice.service.strategy.SocialTokenVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SocialTokenVerificationServiceTest {
	@Mock
	private SocialTokenVerifier googleTokenVerifier;

	@Mock
	private SocialTokenVerifier facebookTokenVerifier;

	private SocialTokenVerificationService socialTokenVerificationService;

	private final String provider = "google";
	private final String token = "test_token";

	@BeforeEach
	void setUp() {
		socialTokenVerificationService = new SocialTokenVerificationService(
				List.of(googleTokenVerifier, facebookTokenVerifier)
		);
	}

	@Test
	@DisplayName("Given valid token, should return valid user payload")
	void verifyToken_validProvider_shouldReturnPayload() {
		SocialUserPayload expectedPayload = new SocialUserPayload("test@example.com");

		when(googleTokenVerifier.supports(provider)).thenReturn(true);
		when(googleTokenVerifier.verify(token)).thenReturn(expectedPayload);

		SocialUserPayload result = socialTokenVerificationService.verifyToken(token, provider);

		assertEquals(expectedPayload, result);
		verify(googleTokenVerifier).verify(token);
		verifyNoInteractions(facebookTokenVerifier);
	}

	@Test
	@DisplayName("Given unsupported provider, should throw an exception")
	void verifyToken_unsupportedProvider_shouldThrowException() {
		when(googleTokenVerifier.supports(provider)).thenReturn(false);
		when(facebookTokenVerifier.supports(provider)).thenReturn(false);

		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> socialTokenVerificationService.verifyToken(token, provider)
		);

		assertEquals("Unsupported provider: google", exception.getMessage());
	}
}
