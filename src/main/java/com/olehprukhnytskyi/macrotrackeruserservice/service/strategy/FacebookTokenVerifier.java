package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.olehprukhnytskyi.exception.ExternalServiceException;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookTokenDebugResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookUserResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.FacebookProperties;
import com.olehprukhnytskyi.util.AuthProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacebookTokenVerifier implements SocialTokenVerifier {
    private final FacebookProperties facebookProperties;
    private final WebClient.Builder webClientBuilder;

    @Override
    public boolean supports(AuthProvider provider) {
        return provider == AuthProvider.FACEBOOK;
    }

    public SocialUserDetails verify(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenVerificationException(AuthErrorCode.INVALID_TOKEN,
                    "Token is not provided or it is blank");
        }
        try {
            WebClient webClient = webClientBuilder.baseUrl("https://graph.facebook.com").build();

            String debugPath = "/debug_token";
            FacebookTokenDebugResponseDto debugResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(debugPath)
                            .queryParam("input_token", token)
                            .queryParam("access_token", facebookProperties.getAppId()
                                    + "|" + facebookProperties.getAppSecret())
                            .build())
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            response -> {
                                log.error("Error verifying Facebook token: {}",
                                        response.statusCode());
                                return Mono.error(new TokenVerificationException(
                                        AuthErrorCode.TOKEN_VERIFICATION_FAILED,
                                        "Error verifying Facebook token"));
                            })
                    .bodyToMono(FacebookTokenDebugResponseDto.class)
                    .block();
            if (debugResponse == null || debugResponse.getData() == null) {
                throw new TokenVerificationException(AuthErrorCode.TOKEN_VERIFICATION_FAILED,
                        "Facebook response is empty or invalid");
            }
            FacebookTokenDebugResponseDto.Data data = debugResponse.getData();
            if (!data.isValid() || !facebookProperties.getAppId().equals(data.getAppId())) {
                throw new TokenVerificationException(AuthErrorCode.INVALID_TOKEN,
                        "Invalid Facebook token");
            }

            String mePath = "/me";
            FacebookUserResponseDto userResponse = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(mePath)
                            .queryParam("fields", "id,name,email")
                            .queryParam("access_token", token)
                            .build())
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            response -> {
                                log.error("Error fetching Facebook user info: {}",
                                        response.statusCode());
                                return Mono.error(new ExternalServiceException(
                                        CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                                        "Error fetching Facebook user info"));
                            })
                    .bodyToMono(FacebookUserResponseDto.class)
                    .block();
            if (userResponse != null && userResponse.getEmail() != null) {
                return new SocialUserDetails(userResponse.getEmail());
            }
            throw new TokenVerificationException(AuthErrorCode.TOKEN_VERIFICATION_FAILED,
                    "Facebook user information is missing or malformed");
        } catch (WebClientResponseException e) {
            log.error("Facebook API error during token verification: {}", e.getMessage(), e);
            throw new ExternalServiceException(CommonErrorCode.UPSTREAM_SERVICE_UNAVAILABLE,
                    "Error connecting to Facebook API", e);
        } catch (TokenVerificationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error verifying Facebook token: {}", e.getMessage(), e);
            throw new ExternalServiceException(CommonErrorCode.INTERNAL_ERROR,
                    "Unexpected error verifying Facebook token", e);
        }
    }
}
