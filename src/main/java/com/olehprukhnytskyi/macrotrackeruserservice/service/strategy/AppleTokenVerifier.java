package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTProcessor;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import com.olehprukhnytskyi.util.AuthProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AppleTokenVerifier implements SocialTokenVerifier {
    private final JWTProcessor<SecurityContext> jwtProcessor;

    public AppleTokenVerifier(
            @Qualifier("appleJwtProcessor") JWTProcessor<SecurityContext> jwtProcessor) {
        this.jwtProcessor = jwtProcessor;
    }

    @Override
    public boolean supports(AuthProvider provider) {
        return provider == AuthProvider.APPLE;
    }

    @Override
    public SocialUserDetails verify(String token) {
        if (token == null || token.isBlank()) {
            throw invalidToken("Apple identity token is missing");
        }
        try {
            JWTClaimsSet claims = jwtProcessor.process(token, null);
            String email = claims.getStringClaim("email");
            Object emailVerified = claims.getClaim("email_verified");
            if (email == null || email.isBlank() || !isVerified(emailVerified)) {
                throw invalidToken("Apple identity token has no verified email");
            }
            return new SocialUserDetails(email);
        } catch (TokenVerificationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Apple identity token verification failed: {}", exception.getMessage());
            throw new TokenVerificationException(
                    AuthErrorCode.INVALID_TOKEN,
                    "Apple identity token is invalid or expired",
                    exception);
        }
    }

    private boolean isVerified(Object claim) {
        return Boolean.TRUE.equals(claim)
                || claim instanceof String value && Boolean.parseBoolean(value);
    }

    private TokenVerificationException invalidToken(String message) {
        return new TokenVerificationException(AuthErrorCode.INVALID_TOKEN, message);
    }
}
