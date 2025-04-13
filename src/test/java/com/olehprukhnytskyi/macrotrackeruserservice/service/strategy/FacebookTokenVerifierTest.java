package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookTokenDebugResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookUserResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
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
    void verify_validToken_shouldReturnValidUserPayload() {
        // Given
        String token = "valid_token";

        FacebookTokenDebugResponse.Data data = new FacebookTokenDebugResponse.Data();
        data.setValid(true);
        data.setAppId("app_id");

        FacebookTokenDebugResponse mockResponse = new FacebookTokenDebugResponse();
        mockResponse.setData(data);
        when(restTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(FacebookTokenDebugResponse.class)
        )).thenReturn(ResponseEntity.ok(mockResponse));

        FacebookUserResponse userResponse = new FacebookUserResponse();
        userResponse.setEmail("user@example.com");
        when(restTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(FacebookUserResponse.class)
        )).thenReturn(ResponseEntity.ok(userResponse));

        // When
        SocialUserPayload payload = facebookTokenVerifier.verify(token);

        // Then
        assertNotNull(payload);
        assertEquals("user@example.com", payload.getEmail());
    }

    @Test
    @DisplayName("Given a null or blank token, check if it throws an exception")
    void verify_nullOrBlankToken_shouldThrowException() {
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
    void verify_invalidToken_shouldThrowException() {
        // Given
        String token = "invalid_token";

        FacebookTokenDebugResponse.Data data = new FacebookTokenDebugResponse.Data();
        data.setValid(false);

        FacebookTokenDebugResponse response = new FacebookTokenDebugResponse();
        response.setData(data);

        when(restTemplate.exchange(
                ArgumentMatchers.anyString(),
                ArgumentMatchers.eq(HttpMethod.GET),
                ArgumentMatchers.isNull(),
                ArgumentMatchers.eq(FacebookTokenDebugResponse.class)
        )).thenReturn(ResponseEntity.ok(response));

        // Then
        assertThrows(TokenVerificationException.class, () -> facebookTokenVerifier.verify(token));
    }

    @Test
    @DisplayName("supports() should return true for 'facebook' provider")
    void supports_shouldReturnTrueForFacebook() {
        assertTrue(facebookTokenVerifier.supports("facebook"));
    }

    @Test
    @DisplayName("supports() should return false for non-facebook provider")
    void supports_shouldReturnFalseForOtherProvider() {
        assertFalse(facebookTokenVerifier.supports("non_existing_provider"));
    }
}
