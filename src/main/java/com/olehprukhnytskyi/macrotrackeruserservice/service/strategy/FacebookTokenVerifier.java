package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookTokenDebugResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.FacebookUserResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@Component
@RequiredArgsConstructor
public class FacebookTokenVerifier implements SocialTokenVerifier {
    @Value("${social.facebook.app_id}")
    private String appId;

    @Value("${social.facebook.app_secret}")
    private String appSecret;

    private final RestTemplate restTemplate;

    @Override
    public boolean supports(String provider) {
        return "facebook".equalsIgnoreCase(provider);
    }

    @Override
    public SocialUserPayload verify(String token) {
        if (token == null || token.isBlank()) {
            throw new TokenVerificationException("Token is not provided or it is blank");
        }
        try {
            String tokenDebugUrl = "https://graph.facebook.com/debug_token"
                    + "?input_token=" + token + "&access_token=" + appId + "|" + appSecret;
            ResponseEntity<FacebookTokenDebugResponse> response = restTemplate.exchange(
                    tokenDebugUrl,
                    HttpMethod.GET,
                    null,
                    FacebookTokenDebugResponse.class
            );
            if (response.getBody() == null || response.getBody().getData() == null) {
                throw new TokenVerificationException("Facebook response is empty or invalid");
            }
            FacebookTokenDebugResponse.Data data = response.getBody().getData();
            if (!data.isValid() || !appId.equals(data.getAppId())) {
                throw new TokenVerificationException("Invalid Facebook token");
            }

            String userInfoUrl = "https://graph.facebook.com/me"
                    + "?fields=id,name,email&access_token=" + token;
            ResponseEntity<FacebookUserResponse> userResponse = restTemplate.exchange(
                    userInfoUrl,
                    HttpMethod.GET,
                    null,
                    FacebookUserResponse.class
            );

            FacebookUserResponse user = userResponse.getBody();
            if (user != null && user.getEmail() != null) {
                return new SocialUserPayload(user.getEmail());
            }
            throw new TokenVerificationException("Facebook user information is"
                    + " missing or malformed");
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            throw new TokenVerificationException("Error connecting to Facebook API: "
                    + e.getMessage(), e);
        }
    }
}
