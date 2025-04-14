package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookTokenDebugResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookUserResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacebookTokenVerifierTest {
    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private FacebookTokenVerifier facebookTokenVerifier;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(facebookTokenVerifier, "appId", "app_id");
        ReflectionTestUtils.setField(facebookTokenVerifier, "appSecret", "app_secret");
    }

    @Test
    @DisplayName("Given a valid token, should return a valid user payload")
    void verify_whenValidToken_shouldReturnValidUserPayload() {
        // Given
        String token = "valid_token";

        FacebookTokenDebugResponseDto.Data data = new FacebookTokenDebugResponseDto.Data();
        data.setValid(true);
        data.setAppId("app_id");

        FacebookTokenDebugResponseDto mockResponse = new FacebookTokenDebugResponseDto();
        mockResponse.setData(data);
        when(restTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(FacebookTokenDebugResponseDto.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        FacebookUserResponseDto userResponse = new FacebookUserResponseDto();
        userResponse.setEmail("user@example.com");
        when(restTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(FacebookUserResponseDto.class)
        )).thenReturn(ResponseEntity.ok(userResponse));

        // When
        SocialUserDetails payload = facebookTokenVerifier.verify(token);

        // Then
        assertNotNull(payload);
        assertEquals("user@example.com", payload.getEmail());
    }

    @Test
    @DisplayName("Given a null or blank token, check if it throws an exception")
    void verify_whenNullOrBlankToken_shouldThrowException() {
        // When
        TokenVerificationException exceptionNull = assertThrows(
                TokenVerificationException.class,
                () -> facebookTokenVerifier.verify(null)
        );
        TokenVerificationException exceptionBlank = assertThrows(
                TokenVerificationException.class,
                () -> facebookTokenVerifier.verify("")
        );

        // Then
        String expected = "Token is not provided or it is blank";
        assertEquals(expected, exceptionNull.getMessage());
        assertEquals(expected, exceptionBlank.getMessage());
    }

    @Test
    @DisplayName("Given an invalid token, should throw TokenVerificationException")
    void verify_whenInvalidToken_shouldThrowException() {
        // Given
        String token = "invalid_token";

        FacebookTokenDebugResponseDto.Data data = new FacebookTokenDebugResponseDto.Data();
        data.setValid(false);

        FacebookTokenDebugResponseDto response = new FacebookTokenDebugResponseDto();
        response.setData(data);

        when(restTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(FacebookTokenDebugResponseDto.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Then
        assertThrows(TokenVerificationException.class, () -> facebookTokenVerifier.verify(token));
    }

    @Test
    @DisplayName("Should return true, when 'facebook' provider")
    void supports_whenFacebookProvider_shouldReturnTrue() {
        assertTrue(facebookTokenVerifier.supports("facebook"));
    }

    @Test
    @DisplayName("Should return false, when non-facebook provider")
    void supports_whenOtherProvider_shouldReturnFalse() {
        assertFalse(facebookTokenVerifier.supports("non_existing_provider"));
    }
}
