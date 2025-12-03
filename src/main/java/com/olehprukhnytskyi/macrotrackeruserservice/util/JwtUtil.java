package com.olehprukhnytskyi.macrotrackeruserservice.util;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.AuthenticationException;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.JwtProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtUtil {
    private final JwtProperties jwtProperties;
    private final RSASSASigner signer;
    private final RSAKey rsaKey;

    public String generateAccessToken(Long userId, String email) {
        return generateToken(
                userId,
                email,
                jwtProperties.getAccessTokenTtlMinutes()
        );
    }

    public String generateRefreshToken(Long userId, String email) {
        return generateToken(
                userId,
                email,
                jwtProperties.getRefreshTokenTtlDays() * 1440
        );
    }

    private String generateToken(Long userId, String email, long minutes) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(email)
                    .claim("id", userId)
                    .issuer("user-service")
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(Duration.ofMinutes(minutes))))
                    .build();
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(jwtProperties.getKeyId())
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new InternalServerException(
                    CommonErrorCode.INTERNAL_ERROR,
                    "Cannot generate token",
                    e
            );
        }
    }

    public SignedJWT parseAndValidate(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            JWSVerifier verifier = new RSASSAVerifier(rsaKey);
            if (!jwt.verify(verifier)) {
                throw new AuthenticationException(AuthErrorCode.TOKEN_VERIFICATION_FAILED,
                        "Invalid refresh token signature");
            }
            Date expires = jwt.getJWTClaimsSet().getExpirationTime();
            if (expires.before(new Date())) {
                throw new AuthenticationException(AuthErrorCode.TOKEN_VERIFICATION_FAILED,
                        "Refresh token expired");
            }
            return jwt;
        } catch (Exception e) {
            throw new AuthenticationException(AuthErrorCode.INVALID_TOKEN,
                    "Invalid refresh token");
        }
    }
}
