package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.olehprukhnytskyi.dto.ProblemDetails;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.exception.error.BaseErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.job.OutboxJob;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.SocialTokenVerificationService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.AuthProvider;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import java.security.KeyPair;
import net.javacrumbs.shedlock.core.LockProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthControllerTest {
    protected static MockMvc mockMvc;

    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private SocialTokenVerificationService tokenVerificationService;
    @MockitoBean
    private RSAKey rsaKey;
    @MockitoBean
    private JWKSet jwkSet;
    @MockitoBean
    private KeyPair keyPair;
    @MockitoBean
    private RSASSASigner rsassaSigner;
    @MockitoBean
    private OutboxJob outboxJob;
    @MockitoBean
    private LockProvider lockProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private UserRepository userRepository;

    private UserDetailsRequestDto userDetailsDto;

    @BeforeAll
    static void beforeAll(
            @Autowired WebApplicationContext applicationContext
    ) {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(applicationContext)
                .build();
    }

    @BeforeEach
    void setUp() {
        userDetailsDto = new UserDetailsRequestDto();
        userDetailsDto.setAge(20);
        userDetailsDto.setGoal(Goal.MAINTAIN);
        userDetailsDto.setWeight(80);
        userDetailsDto.setGender(Gender.MALE);
        userDetailsDto.setHeight(180);
        userDetailsDto.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        userDetailsDto.setBodyType(BodyType.NORMAL);
    }

    @Sql(scripts = "classpath:database/cleanup.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "classpath:database/add-user-for-auth.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "classpath:database/remove-user-for-auth.sql",
            executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    @Test
    @DisplayName("When user exists, should return JWT token")
    void login_whenUserExists_shouldReturnJwtToken() throws Exception {
        // GIven
        LoginRequestDto requestDto = new LoginRequestDto("test@example.com", "password");
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        AuthResponseDto responseDto = AuthResponseDto.builder()
                .accessToken("jwt_access_token")
                .refreshToken("jwt_refresh_token")
                .build();

        when(jwtUtil.generateAccessToken(anyLong(), anyString(), any()))
                .thenReturn("jwt_access_token");
        when(jwtUtil.generateRefreshToken(anyLong(), anyString(), any()))
                .thenReturn("jwt_refresh_token");

        // When
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/auth/login")
                                .content(jsonRequest)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(responseDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When user does not exist, should return unauthorized status")
    void login_whenUserDoesNotExist_shouldReturnUnauthorized() throws Exception {
        // GIven
        LoginRequestDto requestDto = new LoginRequestDto("test@example.com", "password");
        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        BaseErrorCode errorCode = AuthErrorCode.INVALID_CREDENTIALS;
        ProblemDetails problemDetails = ProblemDetails.builder()
                .title(errorCode.getTitle())
                .status(errorCode.getStatus())
                .detail("Invalid email or password")
                .traceId("N/A")
                .code(errorCode.getCode())
                .build();

        // When
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/auth/login")
                                .content(jsonRequest)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(problemDetails);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Sql(scripts = "classpath:database/add-user-for-auth.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "classpath:database/remove-user-for-auth.sql",
            executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    @Test
    @DisplayName("When user already exists, should return unauthorized status")
    void register_whenUserAlreadyExists_shouldReturnUnauthorized() throws Exception {
        // GIven
        RegisterRequestDto requestDto = new RegisterRequestDto(
                "test@example.com", "password", "password");
        requestDto.setUserDetails(userDetailsDto);

        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        BaseErrorCode errorCode = AuthErrorCode.EMAIL_ALREADY_EXISTS;
        ProblemDetails problemDetails = ProblemDetails.builder()
                .title(errorCode.getTitle())
                .status(errorCode.getStatus())
                .traceId("N/A")
                .detail("An account with this email already exists")
                .code(errorCode.getCode())
                .build();

        // When
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/auth/register")
                                .content(jsonRequest)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isConflict())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(problemDetails);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("When user does not exist, should save Outbox event")
    void register_whenUserDoesNotExist_shouldSaveOutboxEvent() throws Exception {
        // GIven
        RegisterRequestDto requestDto = new RegisterRequestDto(
                "test@example.com", "password", "password");
        requestDto.setUserDetails(userDetailsDto);

        String jsonRequest = objectMapper.writeValueAsString(requestDto);

        given(jwtUtil.generateAccessToken(anyLong(), anyString(), any()))
                .willReturn("jwt_access_token");
        given(jwtUtil.generateRefreshToken(anyLong(), anyString(), any()))
                .willReturn("jwt_refresh_token");

        // When
        mockMvc.perform(
                        post("/api/auth/register")
                                .content(jsonRequest)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk());

        User registeredUser = userRepository.findByEmail("test@example.com").orElseThrow();
        assertEquals(2800, registeredUser.getProfile().getWaterGoalMl());
    }

    @Sql(scripts = "classpath:database/add-user-for-auth.sql",
            executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
    @Sql(scripts = "classpath:database/remove-user-for-auth.sql",
            executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
    @Test
    @DisplayName("When user exists, should return JWT token")
    void authenticateWithSocial_whenUserExists_shouldReturnJwtToken() throws Exception {
        // Given
        SocialTokenRequestDto requestDto = new SocialTokenRequestDto(
                "token", AuthProvider.GOOGLE);
        requestDto.setUserDetails(userDetailsDto);

        String requestJson = objectMapper.writeValueAsString(requestDto);
        AuthResponseDto responseDto = AuthResponseDto.builder()
                .accessToken("jwt_access_token")
                .refreshToken("jwt_refresh_token")
                .build();

        given(jwtUtil.generateAccessToken(anyLong(), anyString(), any()))
                .willReturn("jwt_access_token");
        given(jwtUtil.generateRefreshToken(anyLong(), anyString(), any()))
                .willReturn("jwt_refresh_token");
        given(tokenVerificationService.verifyToken(any(), any()))
                .willReturn(new SocialUserDetails("test@example.com"));

        // When
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/auth/social")
                                .content(requestJson)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(responseDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());

        // Clean up
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("When user does not exist, should return JWT token")
    void authenticateWithSocial_whenUserDoesNotExist_shouldReturnJwtToken() throws Exception {
        // Given
        SocialTokenRequestDto requestDto = new SocialTokenRequestDto(
                "token", AuthProvider.GOOGLE);
        requestDto.setUserDetails(userDetailsDto);

        String requestJson = objectMapper.writeValueAsString(requestDto);
        AuthResponseDto responseDto = AuthResponseDto.builder()
                .accessToken("jwt_access_token")
                .refreshToken("jwt_refresh_token")
                .build();

        given(jwtUtil.generateAccessToken(anyLong(), anyString(), any()))
                .willReturn("jwt_access_token");
        given(jwtUtil.generateRefreshToken(anyLong(), anyString(), any()))
                .willReturn("jwt_refresh_token");
        given(tokenVerificationService.verifyToken(any(), any()))
                .willReturn(new SocialUserDetails("test@example.com"));

        // When
        MvcResult mvcResult = mockMvc.perform(
                        post("/api/auth/social")
                                .content(requestJson)
                                .contentType(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isOk())
                .andReturn();

        // Then
        String expected = objectMapper.writeValueAsString(responseDto);
        assertEquals(expected, mvcResult.getResponse().getContentAsString());

        // Clean up
        userRepository.deleteAll();
    }
}
