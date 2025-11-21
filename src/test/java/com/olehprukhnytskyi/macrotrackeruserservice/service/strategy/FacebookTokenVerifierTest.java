package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookTokenDebugResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookUserResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.FacebookProperties;
import com.olehprukhnytskyi.util.AuthProvider;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class FacebookTokenVerifierTest {
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private WebClient webClient;
    @Mock
    private WebClient.RequestHeadersUriSpec<?> requestHeadersUriSpec;
    @Mock
    private WebClient.RequestHeadersSpec<?> requestHeadersSpec;
    @Mock
    private WebClient.ResponseSpec responseSpec;
    @Mock
    private FacebookProperties facebookProperties;

    @InjectMocks
    private FacebookTokenVerifier facebookTokenVerifier;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @DisplayName("Given a valid token, should return a valid user payload")
    void verify_whenValidToken_shouldReturnValidUserPayload() {
        // Given
        FacebookTokenDebugResponseDto.Data data = new FacebookTokenDebugResponseDto.Data();
        data.setValid(true);
        data.setAppId("app_id");

        FacebookTokenDebugResponseDto debugResponse = new FacebookTokenDebugResponseDto();
        debugResponse.setData(data);

        FacebookUserResponseDto userResponse = new FacebookUserResponseDto();
        userResponse.setEmail("user@example.com");

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        lenient().when(facebookProperties.getAppId()).thenReturn("app_id");
        lenient().when(facebookProperties.getAppSecret()).thenReturn("app_secret");

        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(FacebookTokenDebugResponseDto.class)))
                .thenReturn(Mono.just(debugResponse));

        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(FacebookUserResponseDto.class)))
                .thenReturn(Mono.just(userResponse));

        // When
        SocialUserDetails payload = facebookTokenVerifier.verify("valid_token");

        // Then
        assertNotNull(payload);
        assertEquals("user@example.com", payload.getEmail());
    }

    @Test
    @DisplayName("Given a null or blank token, should throw exception")
    void verify_whenNullOrBlankToken_shouldThrowException() {
        assertThrows(TokenVerificationException.class, () -> facebookTokenVerifier.verify(null));
        assertThrows(TokenVerificationException.class, () -> facebookTokenVerifier.verify(""));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    @DisplayName("Given invalid token, should throw TokenVerificationException")
    void verify_whenInvalidToken_shouldThrowException() {
        // Given
        FacebookTokenDebugResponseDto.Data data = new FacebookTokenDebugResponseDto.Data();
        data.setValid(false);
        data.setAppId("app_id");

        FacebookTokenDebugResponseDto debugResponse = new FacebookTokenDebugResponseDto();
        debugResponse.setData(data);

        when(webClientBuilder.baseUrl(anyString())).thenReturn(webClientBuilder);
        when(webClientBuilder.build()).thenReturn(webClient);
        when(webClient.get()).thenReturn((WebClient.RequestHeadersUriSpec) requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.onStatus(any(), any())).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(eq(FacebookTokenDebugResponseDto.class)))
                .thenReturn(Mono.just(debugResponse));

        // When & Then
        assertThrows(TokenVerificationException.class,
                () -> facebookTokenVerifier.verify("invalid_token"));
    }

    @Test
    void supports_whenFacebookProvider_shouldReturnTrue() {
        assertTrue(facebookTokenVerifier.supports(AuthProvider.FACEBOOK));
    }

    @Test
    void supports_whenOtherProvider_shouldThrowException() {
        assertThrows(IllegalArgumentException.class,
                () -> facebookTokenVerifier.supports(AuthProvider
                        .fromString("non_existing_provider")));
    }
}
