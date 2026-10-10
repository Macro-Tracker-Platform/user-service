package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StripeSignatureVerifierTest {
    private final WebBillingProperties properties = new WebBillingProperties();
    private final StripeSignatureVerifier verifier = new StripeSignatureVerifier(properties);
    private final byte[] payload = "{\"id\":\"evt_test\"}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setup() {
        properties.setEnabled(true);
        properties.setStripeWebhookSecret("whsec_test_only");
    }

    @Test
    void validRawBodyAndRotatingSignatureAreAccepted() throws Exception {
        String header = header(payload, Instant.now().getEpochSecond());
        assertThatCode(() -> verifier.verify(payload, "v1=invalid," + header))
                .doesNotThrowAnyException();
    }

    @Test
    void bodyMutationIsRejected() throws Exception {
        String header = header(payload, Instant.now().getEpochSecond());
        assertThatThrownBy(() -> verifier.verify("{}".getBytes(StandardCharsets.UTF_8), header))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void expiredAndFutureSignaturesAreRejected() throws Exception {
        String old = header(payload, Instant.now().minusSeconds(600).getEpochSecond());
        String future = header(payload, Instant.now().plusSeconds(600).getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(payload, old))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> verifier.verify(payload, future))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void malformedOrMissingSignatureIsRejected() {
        assertThatThrownBy(() -> verifier.verify(payload, "t=abc,v1=xyz"))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> verifier.verify(payload, null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void disabledProviderCannotAcceptEvents() throws Exception {
        properties.setEnabled(false);
        String header = header(payload, Instant.now().getEpochSecond());
        assertThatThrownBy(() -> verifier.verify(payload, header))
                .isInstanceOf(ResponseStatusException.class);
    }

    private String header(byte[] body, long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test_only".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body));
    }
}
