package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.SocialTokenVerificationService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {
	@Mock
	private SocialTokenVerificationService tokenVerificationService;

	@Mock
	private JwtUtil jwtUtil;

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private AuthServiceImpl authService;

	private final String provider = "google";
	private final String token = "test_token";

	@Test
	@DisplayName("""
			 Given a valid token and provider,
			 if the user does not exist,
			 should save the user and return a JWT token
			""")
	void authenticateWithSocial_whenUserDoesNotExist_shouldReturnJwtToken() {
		// Given
		SocialUserPayload userPayload = new SocialUserPayload("test@example.com");

		User user = new User();
		user.setEmail("test@example.com");
		user.setAuthProvider("google");

		when(tokenVerificationService.verifyToken(token, provider)).thenReturn(userPayload);
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenReturn(user);
		when(jwtUtil.generateJwtToken(any())).thenReturn("jwt_token");

		// When
		String actualJwt = authService.authenticateWithSocial(provider, token);

		// Then
		assertEquals("jwt_token", actualJwt);
		verify(userRepository).save(any(User.class));
	}

	@Test
	@DisplayName("""
			 Given a valid token and provider,
			 if the user exists,
			 should return a JWT token
			""")
	void authenticateWithSocial_whenUserExists_shouldReturnJwtToken() {
		// Given
		SocialUserPayload userPayload = new SocialUserPayload("test@example.com");

		User user = new User();
		user.setEmail("test@example.com");
		user.setAuthProvider("google");

		when(tokenVerificationService.verifyToken(anyString(), anyString())).thenReturn(userPayload);
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
		when(jwtUtil.generateJwtToken(any())).thenReturn("jwt_token");

		// When
		String actualJwt = authService.authenticateWithSocial(provider, token);

		// Then
		assertEquals("jwt_token", actualJwt);
		verify(userRepository, never()).save(any());
	}
}
