package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.nimbusds.jose.jwk.JWKSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JwksController {
    private final JWKSet jwkSet;

    @GetMapping("/.well-known/jwks.json")
    public ResponseEntity<Map<String, Object>> keys() {
        return ResponseEntity.ok(jwkSet.toJSONObject());
    }
}
