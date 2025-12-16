package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.client.GoalClient;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.AuthenticationException;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import com.olehprukhnytskyi.util.AuthProvider;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mindrot.jbcrypt.BCrypt;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    private SocialTokenVerificationService tokenVerificationService;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserRepository userRepository;
    @Mock
    private UserMapper userMapper;
    @Mock
    private GoalClient goalClient;
    @Mock
    private UserProfileMapper userProfileMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ObjectMapper objectMappere;
    @Mock
    private OutboxRepository outboxRepository;

    @InjectMocks
    private AuthService authService;

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
        userFromDb.setPassword("encoded_string");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(userFromDb));
        when(jwtUtil.generateAccessToken(userFromDb.getId(), userFromDb.getEmail()))
                .thenReturn("jwt_access_token");
        when(jwtUtil.generateRefreshToken(userFromDb.getId(), userFromDb.getEmail()))
                .thenReturn("jwt_refresh_token");
        when(passwordEncoder.matches(loginRequestDto.getPassword(), userFromDb.getPassword()))
                .thenReturn(Boolean.TRUE);

        // When
        AuthResponseDto authResponse = authService.login(loginRequestDto);

        // Then
        assertEquals("jwt_access_token", authResponse.getAccessToken());
        assertEquals("jwt_refresh_token", authResponse.getRefreshToken());
    }

    @Test
    @DisplayName("Should throw an exception, when user exists")
    void register_whenUserExists_shouldThrowException() {
        // Given
        RegisterRequestDto registerRequestDto = new RegisterRequestDto();
        registerRequestDto.setEmail("test@example.com");

        User userFromDb = new User();
        userFromDb.setEmail("test@example.com");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.of(userFromDb));

        // When
        AuthenticationException exception = assertThrows(AuthenticationException.class,
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
        registerRequestDto.setPassword("password");
        registerRequestDto.setConfirmPassword("password");

        User userFromDb = new User();
        userFromDb.setId(1L);
        userFromDb.setEmail("test@example.com");
        userFromDb.setPassword("encoded_string");

        when(userRepository.findByEmail("test@example.com"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any())).thenReturn(userFromDb);
        when(userMapper.toUser(any())).thenReturn(userFromDb);
        when(goalClient.calculateGoal(any())).thenReturn(new GoalResponseDto());
        when(jwtUtil.generateAccessToken(userFromDb.getId(), userFromDb.getEmail()))
                .thenReturn("jwt_access_token");
        when(jwtUtil.generateRefreshToken(userFromDb.getId(), userFromDb.getEmail()))
                .thenReturn("jwt_refresh_token");
        when(passwordEncoder.encode(anyString())).thenReturn("encoded_string");

        // When
        AuthResponseDto authResponse = authService.register(registerRequestDto);

        // Then
        assertEquals("jwt_access_token", authResponse.getAccessToken());
        assertEquals("jwt_refresh_token", authResponse.getRefreshToken());
    }

    @Test
    @DisplayName("Given a valid token and provider,"
            + " if the user does not exist,"
            + " should save the user and return a JWT token")
    void authenticateWithSocial_whenUserDoesNotExist_shouldReturnJwtToken()
            throws JsonProcessingException {
        // Given
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setAuthProvider(AuthProvider.GOOGLE);

        SocialUserDetails userPayload = new SocialUserDetails("test@example.com");
        SocialTokenRequestDto tokenRequestDto = new SocialTokenRequestDto(
                "test_token", AuthProvider.GOOGLE);

        when(tokenVerificationService.verifyToken("test_token", AuthProvider.GOOGLE))
                .thenReturn(userPayload);
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtUtil.generateAccessToken(anyLong(), anyString()))
                .thenReturn("jwt_access_token");
        when(jwtUtil.generateRefreshToken(anyLong(), anyString()))
                .thenReturn("jwt_refresh_token");
        when(objectMappere.writeValueAsString(any())).thenReturn("json");

        // When
        AuthResponseDto authResponse = authService.authenticateWithSocial(tokenRequestDto);

        // Then
        assertEquals("jwt_access_token", authResponse.getAccessToken());
        assertEquals("jwt_refresh_token", authResponse.getRefreshToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("Given a valid token and provider, "
            + "if the user exists,"
            + " should return a JWT token")
    void authenticateWithSocial_whenUserExists_shouldReturnJwtToken() {
        // Given
        User user = new User();
        user.setId(1L);
        user.setEmail("test@example.com");
        user.setAuthProvider(AuthProvider.GOOGLE);

        SocialTokenRequestDto tokenRequestDto = new SocialTokenRequestDto(
                "test_token", AuthProvider.GOOGLE);

        when(tokenVerificationService.verifyToken(anyString(), any()))
                .thenReturn(new SocialUserDetails("test@example.com"));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(anyLong(), anyString()))
                .thenReturn("jwt_access_token");
        when(jwtUtil.generateRefreshToken(anyLong(), anyString()))
                .thenReturn("jwt_refresh_token");

        // When
        AuthResponseDto authResponse = authService.authenticateWithSocial(tokenRequestDto);

        // Then
        assertEquals("jwt_access_token", authResponse.getAccessToken());
        assertEquals("jwt_refresh_token", authResponse.getRefreshToken());
        verify(userRepository, never()).save(any());
    }
}
