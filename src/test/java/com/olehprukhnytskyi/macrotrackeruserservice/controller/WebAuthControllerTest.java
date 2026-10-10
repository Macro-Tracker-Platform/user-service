package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.olehprukhnytskyi.exception.GlobalExceptionHandler;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.AuthenticationException;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.HttpStatusExceptionHandler;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.JwtProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebAuthProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WebAuthControllerTest {
    private final AuthService auth = mock(AuthService.class);
    private final WebAuthProperties properties = new WebAuthProperties();
    private final JwtProperties jwt = new JwtProperties();

    private MockMvc mvc() {
        jwt.setRefreshTokenTtlDays(30L);
        return MockMvcBuilders.standaloneSetup(new WebAuthController(auth, properties, jwt))
                .setControllerAdvice(new GlobalExceptionHandler(), new HttpStatusExceptionHandler())
                .build();
    }

    @Test
    void loginKeepsRefreshTokenOutOfJsonAndInHostOnlySecureCookie() throws Exception {
        when(auth.login(any())).thenReturn(new AuthResponseDto("access", "refresh-secret"));
        var result = mvc().perform(post("/api/auth/web/login")
                .header("Origin", "https://macrotracker.uk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").value("access"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist()).andReturn();
        assertThat(result.getResponse().getHeaders("Set-Cookie").getFirst())
                .contains("__Host-mt_refresh=refresh-secret", "Path=/", "Secure", "HttpOnly",
                        "SameSite=Lax", "Max-Age=2592000").doesNotContain("Domain=");
        assertThat(result.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void cookieCannotBeRefreshedFromMissingSiblingOrAttackerOrigin() throws Exception {
        var mvc = mvc();
        for (String origin : new String[]{"https://evil.example", "https://evil.macrotracker.uk",
                "null", "https://macrotracker.uk.evil.example"}) {
            mvc.perform(post("/api/auth/web/refresh").header("Origin", origin)
                    .cookie(new Cookie("__Host-mt_refresh", "secret")))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/auth/web/refresh")
                .cookie(new Cookie("__Host-mt_refresh", "secret")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(auth);
    }

    @Test
    void refreshRequiresCookieAndRotatesIt() throws Exception {
        var mvc = mvc();
        mvc.perform(post("/api/auth/web/refresh").header("Origin", "https://macrotracker.uk"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("SIGN_IN_REQUIRED"))
                .andExpect(header().string("Cache-Control", "no-store"));
        when(auth.refreshToken("old")).thenReturn(new AuthResponseDto("new-access", "new-refresh"));
        var result = mvc.perform(post("/api/auth/web/refresh")
                .header("Origin", "https://macrotracker.uk")
                .cookie(new Cookie("__Host-mt_refresh", "old")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access")).andReturn();
        assertThat(result.getResponse().getHeaders("Set-Cookie").getFirst())
                .contains("__Host-mt_refresh=new-refresh");
    }

    @Test
    void socialLoginWithoutBrowserChallengeNeverReachesProvider() throws Exception {
        mvc().perform(post("/api/auth/web/social").header("Origin", "https://macrotracker.uk")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"GOOGLE\",\"token\":\"untrusted\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(auth);
    }

    @Test
    void logoutExpiresBothCookiesWithoutReturningCredentials() throws Exception {
        var response = mvc().perform(post("/api/auth/web/logout")
                .header("Origin", "https://macrotracker.uk"))
                .andExpect(status().isNoContent()).andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).hasSize(2)
                .allSatisfy(cookie -> assertThat(cookie).contains("Max-Age=0", "HttpOnly"));
    }

    @Test
    void openingPostOnlyRefreshInBrowserReturns405WithAllowHeader() throws Exception {
        mvc().perform(get("/api/auth/web/refresh"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", "POST"))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("HTTP_405"));
        verifyNoInteractions(auth);
    }

    @Test
    void expiredCookieStillUsesTheExistingAuthenticationErrorContract() throws Exception {
        when(auth.refreshToken("expired")).thenThrow(new AuthenticationException(
                AuthErrorCode.INVALID_TOKEN, "Invalid refresh token"));
        mvc().perform(post("/api/auth/web/refresh")
                .header("Origin", "https://macrotracker.uk")
                .cookie(new Cookie("__Host-mt_refresh", "expired")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(AuthErrorCode.INVALID_TOKEN.getCode()));
    }

    @Test
    void actualUnexpectedFailureStillReturns500WithoutExposingItsMessage() throws Exception {
        when(auth.refreshToken("old")).thenThrow(new IllegalStateException("private failure"));
        mvc().perform(post("/api/auth/web/refresh")
                .header("Origin", "https://macrotracker.uk")
                .cookie(new Cookie("__Host-mt_refresh", "old")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }

}
