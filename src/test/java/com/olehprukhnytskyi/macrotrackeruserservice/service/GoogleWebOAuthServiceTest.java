package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class GoogleWebOAuthServiceTest {
    private final WebAuthProperties properties = new WebAuthProperties();
    private final RestClient.Builder http = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(http).build();

    private GoogleWebOAuthService service() {
        var builder = mock(RestClient.Builder.class, RETURNS_SELF);
        when(builder.build()).thenReturn(http.build());
        return new GoogleWebOAuthService(properties, builder);
    }

    @Test
    void missingSecretFailsClosedWithoutProviderCall() {
        properties.setGoogleClientId("web");
        var service = service();
        assertThat(service.configured()).isFalse();
        assertThatThrownBy(() -> service.exchange("code", "https://macrotracker.uk"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("503");
        server.verify();
    }

    @Test
    void exchangeUsesServerSecretAndAllowedOriginAndReturnsOnlyIdToken() throws Exception {
        properties.setGoogleClientId("web");
        properties.setGoogleClientSecret("secret");
        String token = token("web");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(org.springframework.test.web.client.match.MockRestRequestMatchers
                        .content().string(org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("client_secret=secret"),
                                org.hamcrest.Matchers.containsString(
                                        "redirect_uri=https%3A%2F%2Fmacrotracker.uk"),
                                org.hamcrest.Matchers.containsString(
                                        "grant_type=authorization_code"))))
                .andRespond(withSuccess("{\"id_token\":\"" + token
                        + "\",\"access_token\":\"unused\"}", MediaType.APPLICATION_JSON));
        assertThat(service().exchange("code", "https://macrotracker.uk")).isEqualTo(token);
        server.verify();
    }

    @Test
    void wrongAudienceAndInvalidCodeFailWithoutLeakingProviderResponse() throws Exception {
        properties.setGoogleClientId("web");
        properties.setGoogleClientSecret("secret");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withSuccess("{\"id_token\":\"" + token("native") + "\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andRespond(withUnauthorizedRequest().body("private-provider-details"));
        var service = service();
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> service.exchange("code", "https://macrotracker.uk"))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("SOCIAL_LOGIN_INVALID")
                    .hasMessageNotContaining("private-provider-details");
        }
        server.verify();
    }

    @Test
    void untrustedOriginNeverReachesGoogle() {
        properties.setGoogleClientId("web");
        properties.setGoogleClientSecret("secret");
        assertThatThrownBy(() -> service().exchange("code", "https://evil.example"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        server.verify();
    }

    private String token(String audience) throws Exception {
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),
                new JWTClaimsSet.Builder().audience(audience).build());
        jwt.sign(new MACSigner(new byte[32]));
        return jwt.serialize();
    }
}
