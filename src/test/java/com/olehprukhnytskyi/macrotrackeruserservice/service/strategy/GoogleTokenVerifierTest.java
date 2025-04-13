package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.GeneralSecurityException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

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
	void verify_validToken_shouldReturnValidUserPayload() throws Exception {
		// Given
		String token = "valid_token";

		when(googleIdTokenVerifier.verify(token)).thenReturn(googleIdToken);
		when(googleIdToken.getPayload()).thenReturn(payload);
		when(payload.getEmail()).thenReturn("test@example.com");

		// When
		SocialUserPayload userPayload = googleTokenVerifier.verify(token);

		// Then
		assertEquals("test@example.com", userPayload.getEmail());
	}

	@Test
	@DisplayName("Given a null or blank token, check if it throws an exception")
	void verify_nullOrBlankToken_shouldThrowException()
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
	@DisplayName("Given a malformed token, check if it throws an exception")
	void verify_malformedToken_shouldThrowException() throws Exception {
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
	@DisplayName("supports() should return true for 'google' provider")
	void supports_shouldReturnTrueForGoogle() {
		assertTrue(googleTokenVerifier.supports("google"));
	}

	@Test
	@DisplayName("supports() should return false for non-google provider")
	void supports_shouldReturnFalseForOtherProvider() {
		assertFalse(googleTokenVerifier.supports("non_existing_provider"));
	}
}
