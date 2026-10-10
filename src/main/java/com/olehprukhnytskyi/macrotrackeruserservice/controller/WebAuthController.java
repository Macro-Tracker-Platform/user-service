package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.nimbusds.jwt.SignedJWT;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebAuthSessionDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.JwtProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebAuthProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import com.olehprukhnytskyi.util.AuthProvider;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth/web")
public class WebAuthController {
    private static final String REFRESH = "__Host-mt_refresh";
    private static final String NONCE = "__Host-mt_nonce";
    private final AuthService auth;
    private final WebAuthProperties properties;
    private final JwtProperties jwtProperties;

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of(
                "googleClientId", properties.getGoogleClientId(),
                "appleServiceId", properties.getAppleServiceId(),
                "appleRedirectUri", properties.getAppleRedirectUri()));
    }

    @PostMapping("/login")
    public ResponseEntity<WebAuthSessionDto> login(
            @RequestHeader(value = "Origin", required = false) String origin,
            @RequestBody @Valid LoginRequestDto request) {
        requireOrigin(origin);
        return session(auth.login(request));
    }

    @PostMapping("/challenge")
    public ResponseEntity<Map<String, String>> challenge(
            @RequestHeader(value = "Origin", required = false) String origin) {
        requireOrigin(origin);
        String nonce = UUID.randomUUID().toString();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(NONCE, nonce, Duration.ofMinutes(5)))
                .body(Map.of("nonce", nonce));
    }

    @PostMapping("/social")
    public ResponseEntity<WebAuthSessionDto> social(
            @RequestHeader(value = "Origin", required = false) String origin,
            @CookieValue(name = NONCE, required = false) String nonce,
            @RequestBody @Valid SocialTokenRequestDto request) {
        requireOrigin(origin);
        String audience = request.getProvider() == AuthProvider.GOOGLE
                ? properties.getGoogleClientId() : request.getProvider() == AuthProvider.APPLE
                    ? properties.getAppleServiceId() : "";
        try {
            var claims = SignedJWT.parse(request.getToken()).getJWTClaimsSet();
            String tokenNonce = claims.getStringClaim("nonce");
            if (audience.isBlank() || !claims.getAudience().contains(audience)
                    || nonce == null || tokenNonce == null
                    || !MessageDigest.isEqual(nonce.getBytes(StandardCharsets.UTF_8),
                            tokenNonce.getBytes(StandardCharsets.UTF_8))) {
                throw new IllegalArgumentException("Invalid web login challenge");
            }
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SOCIAL_LOGIN_INVALID");
        }
        // Signature, issuer, expiry and audience are verified by the existing provider verifier.
        // Web login cannot bypass the app's required profile onboarding to create an account.
        request.setUserDetails(null);
        return session(auth.authenticateWithSocial(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<WebAuthSessionDto> refresh(
            @RequestHeader(value = "Origin", required = false) String origin,
            @CookieValue(name = REFRESH, required = false) String refresh) {
        requireOrigin(origin);
        if (refresh == null || refresh.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "SIGN_IN_REQUIRED");
        }
        return session(auth.refreshToken(refresh));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "Origin", required = false) String origin) {
        requireOrigin(origin);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(REFRESH, "", Duration.ZERO),
                        cookie(NONCE, "", Duration.ZERO)).build();
    }

    private ResponseEntity<WebAuthSessionDto> session(AuthResponseDto tokens) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookie(REFRESH, tokens.getRefreshToken(),
                        Duration.ofDays(jwtProperties.getRefreshTokenTtlDays())),
                        cookie(NONCE, "", Duration.ZERO))
                .body(new WebAuthSessionDto(tokens.getAccessToken()));
    }

    private String cookie(String name, String value, Duration lifetime) {
        return ResponseCookie.from(name, value).httpOnly(true).secure(true)
                .sameSite("Lax").path("/").maxAge(lifetime).build().toString();
    }

    private void requireOrigin(String origin) {
        if (origin == null || !properties.getAllowedOrigins().contains(origin)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "WEB_ORIGIN_NOT_ALLOWED");
        }
    }
}
