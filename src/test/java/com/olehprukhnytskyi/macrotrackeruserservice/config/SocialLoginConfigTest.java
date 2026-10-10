package com.olehprukhnytskyi.macrotrackeruserservice.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.AppleProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GoogleProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebAuthProperties;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SocialLoginConfigTest {
    @Test
    void googleAcceptsConfiguredNativeAndWebAudiences() {
        GoogleProperties nativeProperties = new GoogleProperties();
        nativeProperties.setClientId("native-client");
        WebAuthProperties web = new WebAuthProperties();
        web.setGoogleClientId("web-client");

        var verifier = new SocialLoginConfig(nativeProperties, web).googleIdTokenVerifier();

        assertEquals(Set.of("native-client", "web-client"), Set.copyOf(verifier.getAudience()));
    }

    @Test
    void unconfiguredGoogleWebLoginKeepsNativeAudienceRestriction() {
        GoogleProperties nativeProperties = new GoogleProperties();
        nativeProperties.setClientId("native-client");
        WebAuthProperties web = new WebAuthProperties();
        web.setGoogleClientId(" ");

        var verifier = new SocialLoginConfig(nativeProperties, web).googleIdTokenVerifier();

        assertEquals(Set.of("native-client"), Set.copyOf(verifier.getAudience()));
    }

    @Test
    void googleDeduplicatesSharedClientId() {
        GoogleProperties nativeProperties = new GoogleProperties();
        nativeProperties.setClientId("shared-client");
        WebAuthProperties web = new WebAuthProperties();
        web.setGoogleClientId("shared-client");

        var verifier = new SocialLoginConfig(nativeProperties, web).googleIdTokenVerifier();

        assertEquals(1, verifier.getAudience().size());
    }

    @Test
    void appleAcceptsNativeBundleAndConfiguredWebService() throws Exception {
        var verifier = appleClaimsVerifier("web-service");

        assertDoesNotThrow(() -> verifier.verify(appleClaims("native-bundle"), null));
        assertDoesNotThrow(() -> verifier.verify(appleClaims("web-service"), null));
    }

    @Test
    void appleRejectsUnrelatedAudienceAndIssuer() throws Exception {
        var verifier = appleClaimsVerifier("web-service");

        assertThrows(BadJWTException.class,
                () -> verifier.verify(appleClaims("unrelated-client"), null));
        JWTClaimsSet forgedIssuer = new JWTClaimsSet.Builder(appleClaims("web-service"))
                .issuer("https://untrusted.example").build();
        assertThrows(BadJWTException.class, () -> verifier.verify(forgedIssuer, null));
    }

    @Test
    void unconfiguredAppleWebLoginDoesNotPermitWebAudience() throws Exception {
        var verifier = appleClaimsVerifier(" ");

        assertDoesNotThrow(() -> verifier.verify(appleClaims("native-bundle"), null));
        assertThrows(BadJWTException.class,
                () -> verifier.verify(appleClaims("web-service"), null));
    }

    private JWTClaimsSetVerifier<SecurityContext> appleClaimsVerifier(String serviceId)
            throws Exception {
        AppleProperties nativeProperties = new AppleProperties();
        nativeProperties.setClientIds(Set.of("native-bundle"));
        WebAuthProperties web = new WebAuthProperties();
        web.setAppleServiceId(serviceId);
        var processor = (DefaultJWTProcessor<SecurityContext>) new AppleSocialLoginConfig()
                .appleJwtProcessor(nativeProperties, web);
        return processor.getJWTClaimsSetVerifier();
    }

    private JWTClaimsSet appleClaims(String audience) {
        Instant now = Instant.now();
        return new JWTClaimsSet.Builder()
                .issuer("https://appleid.apple.com")
                .audience(audience)
                .subject("synthetic-subject")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .claim("email", "synthetic@example.com")
                .claim("email_verified", true)
                .build();
    }
}
