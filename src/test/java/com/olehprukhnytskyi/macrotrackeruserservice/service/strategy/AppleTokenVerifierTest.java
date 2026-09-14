package com.olehprukhnytskyi.macrotrackeruserservice.service.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTProcessor;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.TokenVerificationException;
import com.olehprukhnytskyi.util.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AppleTokenVerifierTest {
    @Mock
    private JWTProcessor<SecurityContext> jwtProcessor;

    private AppleTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new AppleTokenVerifier(jwtProcessor);
    }

    @Test
    void supportsOnlyAppleProvider() {
        assertTrue(verifier.supports(AuthProvider.APPLE));
        assertFalse(verifier.supports(AuthProvider.GOOGLE));
    }

    @Test
    void verifyReturnsVerifiedAppleEmail() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("email", "private@privaterelay.appleid.com")
                .claim("email_verified", true)
                .build();
        when(jwtProcessor.process("valid-token", null)).thenReturn(claims);

        SocialUserDetails result = verifier.verify("valid-token");

        assertEquals("private@privaterelay.appleid.com", result.getEmail());
    }

    @Test
    void verifyAcceptsAppleStringEmailVerifiedClaim() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("email", "user@example.com")
                .claim("email_verified", "true")
                .build();
        when(jwtProcessor.process("valid-token", null)).thenReturn(claims);

        assertEquals("user@example.com", verifier.verify("valid-token").getEmail());
    }

    @Test
    void verifyRejectsMissingOrUnverifiedEmail() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .claim("email", "user@example.com")
                .claim("email_verified", false)
                .build();
        when(jwtProcessor.process("unverified-token", null)).thenReturn(claims);

        TokenVerificationException exception = assertThrows(
                TokenVerificationException.class,
                () -> verifier.verify("unverified-token"));

        assertEquals("Apple identity token has no verified email", exception.getMessage());
    }

    @Test
    void verifyRejectsBlankTokenWithoutCallingProcessor() {
        TokenVerificationException exception = assertThrows(
                TokenVerificationException.class,
                () -> verifier.verify(" "));

        assertEquals("Apple identity token is missing", exception.getMessage());
    }

    @Test
    void verifyMapsSignatureOrClaimsFailureToInvalidToken() throws Exception {
        when(jwtProcessor.process("invalid-token", null))
                .thenThrow(new BadJOSEException("Invalid signature"));

        TokenVerificationException exception = assertThrows(
                TokenVerificationException.class,
                () -> verifier.verify("invalid-token"));

        assertEquals("Apple identity token is invalid or expired", exception.getMessage());
    }
}
