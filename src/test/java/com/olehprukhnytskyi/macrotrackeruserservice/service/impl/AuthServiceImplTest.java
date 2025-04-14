package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.AuthenticationException;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.SocialTokenVerificationService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

	@Test
	@DisplayName("Should throw an exception, when user does not exist")
	void login_whenUserDoesNotExist_shouldThrowException() {
		// Given
		LoginRequestDto loginRequestDto = new LoginRequestDto();
		loginRequestDto.setEmail("test@example.com");
		loginRequestDto.setPassword("password");

		when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

		// When
		AuthenticationException exception = assertThrows(AuthenticationException.class,
				() -> authService.login(loginRequestDto));

		// Then
		String expected = "Invalid email or password";
		assertEquals(expected, exception.getMessage());
	}

	@Test
	@DisplayName("Should throw an exception, when passwords do not match")
	void login_whenPasswordsDoNotMatch_shouldThrowException() {
		// Given
		LoginRequestDto loginRequestDto = new LoginRequestDto();
		loginRequestDto.setEmail("test@example.com");
		loginRequestDto.setPassword("wrongPassword");

		User userFromDb = new User();
		userFromDb.setEmail("test@example.com");
		userFromDb.setPassword(BCrypt.hashpw("correctPassword", BCrypt.gensalt()));

		when(userRepository.findByEmail("test@example.com"))
				.thenReturn(Optional.of(userFromDb));

		// When
		AuthenticationException exception = assertThrows(AuthenticationException.class,
				() -> authService.login(loginRequestDto));

		// Then
		String expected = "Invalid email or password";
		assertEquals(expected, exception.getMessage());
	}

	@Test
	@DisplayName("Should return a JWT token, when passwords match")
	void login_whenPasswordsMatch_shouldReturnJwtToken() {
		// Given
		LoginRequestDto loginRequestDto = new LoginRequestDto();
		loginRequestDto.setEmail("test@example.com");
		loginRequestDto.setPassword("password");

		User userFromDb = new User();
		userFromDb.setEmail("test@example.com");
		userFromDb.setPassword(BCrypt.hashpw("password", BCrypt.gensalt()));

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(userFromDb));
		when(jwtUtil.generateJwtToken(userFromDb)).thenReturn("jwt_token");

		// When
		String actualJwt = authService.login(loginRequestDto);

		// Then
		assertEquals("jwt_token", actualJwt);
	}

	@Test
	@DisplayName("Should throw an exception, when user exists")
	void register_whenUserExists_shouldThrowException() {
		// Given
		RegisterRequestDto registerRequestDto = new RegisterRequestDto();
		registerRequestDto.setEmail("test@example.com");

		User userFromDb = new User();
		userFromDb.setEmail("test@example.com");

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(userFromDb));

		// When
		IllegalStateException exception = assertThrows(IllegalStateException.class,
				() -> authService.register(registerRequestDto));

		// Then
		String expected = "An account with this email already exists";
		assertEquals(expected, exception.getMessage());
	}

	@Test
	@DisplayName("Should return JWT token, when user does not exist")
	void register_whenUserDoesNotExist_shouldReturnJwtToken() {
		// Given
		RegisterRequestDto registerRequestDto = new RegisterRequestDto();
		registerRequestDto.setEmail("test@example.com");

		User userFromDb = new User();
		userFromDb.setEmail("test@example.com");

		when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
		when(userRepository.save(any())).thenReturn(userFromDb);
		when(jwtUtil.generateJwtToken(userFromDb)).thenReturn("jwt_token");

		// When
		String actualJwt = authService.register(registerRequestDto);

		// Then
		assertEquals("jwt_token", actualJwt);
	}

	@Test
	@DisplayName("""
			 Given a valid token and provider,
			 if the user does not exist,
			 should save the user and return a JWT token
			""")
	void authenticateWithSocial_whenUserDoesNotExist_shouldReturnJwtToken() {
		// Given
		SocialUserDetails userPayload = new SocialUserDetails("test@example.com");

		User user = new User();
		user.setEmail("test@example.com");
		user.setAuthProvider("google");

		SocialTokenRequestDto tokenRequestDto = new SocialTokenRequestDto("test_token", "google");

		when(tokenVerificationService.verifyToken("test_token", "google")).thenReturn(userPayload);
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
		when(userRepository.save(any(User.class))).thenReturn(user);
		when(jwtUtil.generateJwtToken(any())).thenReturn("jwt_token");

		// When
		String actualJwt = authService.authenticateWithSocial(tokenRequestDto);

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
		SocialUserDetails userPayload = new SocialUserDetails("test@example.com");

		User user = new User();
		user.setEmail("test@example.com");
		user.setAuthProvider("google");

		SocialTokenRequestDto tokenRequestDto = new SocialTokenRequestDto("test_token", "google");

		when(tokenVerificationService.verifyToken(anyString(), anyString())).thenReturn(userPayload);
		when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
		when(jwtUtil.generateJwtToken(any())).thenReturn("jwt_token");

		// When
		String actualJwt = authService.authenticateWithSocial(tokenRequestDto);

		// Then
		assertEquals("jwt_token", actualJwt);
		verify(userRepository, never()).save(any());
	}
}
