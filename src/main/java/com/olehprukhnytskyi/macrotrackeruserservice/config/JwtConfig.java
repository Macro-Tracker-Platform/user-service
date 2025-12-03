package com.olehprukhnytskyi.macrotrackeruserservice.config;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.JwtProperties;
import java.io.StringReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import lombok.RequiredArgsConstructor;
import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@RequiredArgsConstructor
public class JwtConfig {
    private final JwtProperties jwtProperties;

    @Bean
    public KeyPair keyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Cannot generate RSA key pair", e);
        }
    }

    @Bean
    public RSAKey rsaKey() {
        try {
            PrivateKey privateKey = readPrivateKey(jwtProperties.getPrivateKey());
            PublicKey publicKey = readPublicKey(jwtProperties.getPublicKey());
            return new RSAKey.Builder((RSAPublicKey) publicKey)
                    .privateKey(privateKey)
                    .keyID(jwtProperties.getKeyId())
                    .build();
        } catch (Exception e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Failed to load RSA keys", e);
        }
    }

    @Bean
    public JWKSet jwkSet(RSAKey rsaJwk) {
        return new JWKSet(rsaJwk.toPublicJWK());
    }

    @Bean
    public RSASSASigner rsaSigner(RSAKey rsaJwk) {
        try {
            return new RSASSASigner(rsaJwk.toPrivateKey());
        } catch (JOSEException e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Cannot generate RSA key pair", e);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private PrivateKey readPrivateKey(String key) throws Exception {
        String pem = normalizePem(key, "PRIVATE KEY");
        PemReader pemReader = new PemReader(new StringReader(pem));
        PemObject pemObject = pemReader.readPemObject();
        pemReader.close();
        if (pemObject == null) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Invalid private key PEM");
        }
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(pemObject.getContent());
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private PublicKey readPublicKey(String key) throws Exception {
        String pem = normalizePem(key, "PUBLIC KEY");
        PemReader pemReader = new PemReader(new StringReader(pem));
        PemObject pemObject = pemReader.readPemObject();
        pemReader.close();
        if (pemObject == null) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "Invalid public key PEM");
        }
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pemObject.getContent());
        return KeyFactory.getInstance("RSA").generatePublic(keySpec);
    }

    private String normalizePem(String pem, String type) {
        pem = pem.replace("\\n", "\n").trim();
        if (!pem.startsWith("-----BEGIN " + type + "-----")) {
            pem = "-----BEGIN " + type + "-----\n" + pem;
        }
        if (!pem.endsWith("-----END " + type + "-----")) {
            pem = pem + "\n-----END " + type + "-----";
        }
        return pem;
    }
}
