package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
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

		when(googleTokenVerifier.supports("google")).thenReturn(true);
		when(googleTokenVerifier.verify("test_token")).thenReturn(expectedPayload);

		// When
		SocialUserDetails result = socialTokenVerificationService.verifyToken("test_token", "google");

		// Then
		assertEquals(expectedPayload, result);
		verify(googleTokenVerifier).verify("test_token");
		verifyNoInteractions(facebookTokenVerifier);
	}

	@Test
	@DisplayName("Given unsupported provider, should throw an exception")
	void verifyToken_whenUnsupportedProvider_shouldThrowException() {
		// Given
		when(googleTokenVerifier.supports("google")).thenReturn(false);
		when(facebookTokenVerifier.supports("google")).thenReturn(false);

		// When
		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> socialTokenVerificationService.verifyToken("test_token", "google")
		);

		// Then
		assertEquals("Unsupported provider: google", exception.getMessage());
	}
}
