package com.olehprukhnytskyi.macrotrackeruserservice;

import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class MacroTrackerUserServiceApplicationTests {
    @MockitoBean
    private RSAKey rsaKey;
    @MockitoBean
    private JWKSet jwkSet;
    @MockitoBean
    private KeyPair keyPair;
    @MockitoBean
    private RSASSASigner rsassaSigner;

    @Test
    void contextLoads() {
    }
}
