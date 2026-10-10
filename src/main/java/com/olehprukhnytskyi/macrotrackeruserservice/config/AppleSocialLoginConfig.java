package com.olehprukhnytskyi.macrotrackeruserservice.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.AppleProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebAuthProperties;
import java.net.MalformedURLException;
import java.net.URI;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppleSocialLoginConfig {
    private static final String APPLE_ISSUER = "https://appleid.apple.com";
    private static final int HTTP_TIMEOUT_MILLIS = 3_000;
    private static final int MAX_JWK_SET_BYTES = 100_000;

    @Bean
    @Qualifier("appleJwtProcessor")
    public JWTProcessor<SecurityContext> appleJwtProcessor(
            AppleProperties properties, WebAuthProperties webAuthProperties)
            throws MalformedURLException {
        DefaultResourceRetriever resourceRetriever = new DefaultResourceRetriever(
                HTTP_TIMEOUT_MILLIS, HTTP_TIMEOUT_MILLIS, MAX_JWK_SET_BYTES);
        JWKSource<SecurityContext> jwkSource = JWKSourceBuilder
                .<SecurityContext>create(
                        URI.create(properties.getJwkSetUri()).toURL(), resourceRetriever)
                .retrying(true)
                .build();

        DefaultJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
        processor.setJWSKeySelector(new JWSVerificationKeySelector<>(
                JWSAlgorithm.RS256, jwkSource));

        JWTClaimsSet expectedClaims = new JWTClaimsSet.Builder()
                .issuer(APPLE_ISSUER)
                .build();
        DefaultJWTClaimsVerifier<SecurityContext> claimsVerifier =
                new DefaultJWTClaimsVerifier<>(
                        Stream.concat(properties.getClientIds().stream(),
                                        Stream.of(webAuthProperties.getAppleServiceId()))
                                .filter(value -> value != null && !value.isBlank())
                                .collect(Collectors.toSet()),
                        expectedClaims,
                        Set.of("sub", "iss", "aud", "iat", "exp", "email", "email_verified"),
                        Set.of());
        claimsVerifier.setMaxClockSkew(60);
        processor.setJWTClaimsSetVerifier(claimsVerifier);
        return processor;
    }
}
