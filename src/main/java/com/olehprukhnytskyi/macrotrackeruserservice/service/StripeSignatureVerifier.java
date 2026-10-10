package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@RequiredArgsConstructor
public class StripeSignatureVerifier {
    private final WebBillingProperties properties;

    public void verify(byte[] payload, String header) {
        if (!properties.isEnabled() || properties.getStripeWebhookSecret() == null
                || properties.getStripeWebhookSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "STRIPE_WEBHOOK_NOT_CONFIGURED");
        }
        try {
            long timestamp = 0;
            List<String> signatures = new ArrayList<>();
            for (String part : header.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && "t".equals(pair[0])) {
                    timestamp = Long.parseLong(pair[1]);
                } else if (pair.length == 2 && "v1".equals(pair[0])) {
                    signatures.add(pair[1]);
                }
            }
            long now = Instant.now().getEpochSecond();
            if (payload.length > 1048576 || timestamp < now - 300 || timestamp > now + 300) {
                throw invalid();
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getStripeWebhookSecret()
                    .getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
            byte[] expected = mac.doFinal(payload);
            for (String signature : signatures) {
                if (signature.matches("[a-fA-F0-9]{64}")
                        && MessageDigest.isEqual(expected, HexFormat.of().parseHex(signature))) {
                    return;
                }
            }
        } catch (GeneralSecurityException | IllegalArgumentException
                 | NullPointerException ignored) {
            throw invalid();
        }
        throw invalid();
    }

    private ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_STRIPE_SIGNATURE");
    }
}
