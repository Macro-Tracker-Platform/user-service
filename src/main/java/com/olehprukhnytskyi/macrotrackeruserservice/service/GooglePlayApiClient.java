package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@Component
@RequiredArgsConstructor
public class GooglePlayApiClient {
    private static final String ANDROID_PUBLISHER_SCOPE =
            "https://www.googleapis.com/auth/androidpublisher";
    private static final String API_BASE_URL = "https://androidpublisher.googleapis.com";

    private final WebClient.Builder webClientBuilder;
    private final GooglePlayProperties properties;

    public GooglePlaySubscriptionSnapshot getSubscription(String purchaseToken) {
        JsonNode response;
        try {
            response = client().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/androidpublisher/v3/applications/{packageName}"
                                    + "/purchases/subscriptionsv2/tokens/{token}")
                            .build(properties.getPackageName(), purchaseToken))
                    .header("Authorization", "Bearer " + accessToken())
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();
        } catch (WebClientResponseException exception) {
            throw googlePlayFailure("subscriptions.get", exception);
        }
        if (response == null || response.path("lineItems").isEmpty()) {
            throw new IllegalStateException("Google Play returned no subscription line items");
        }
        JsonNode lineItem = response.path("lineItems").get(0);
        JsonNode autoRenewingPlan = lineItem.path("autoRenewingPlan");
        String basePlanId = nullableText(lineItem.path("offerDetails").path("basePlanId"));
        String offerId = nullableText(lineItem.path("offerDetails").path("offerId"));
        return new GooglePlaySubscriptionSnapshot(
                lineItem.path("productId").asText(),
                basePlanId,
                offerId,
                response.path("subscriptionState").asText(),
                parseInstant(response.path("startTime")),
                parseInstant(lineItem.path("expiryTime")),
                autoRenewingPlan.path("autoRenewEnabled").asBoolean(false),
                "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED".equals(
                        response.path("acknowledgementState").asText())
        );
    }

    public void acknowledge(String productId, String purchaseToken) {
        try {
            client().post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/androidpublisher/v3/applications/{packageName}"
                                    + "/purchases/subscriptions/{productId}/tokens/"
                                    + "{token}:acknowledge")
                            .build(properties.getPackageName(), productId, purchaseToken))
                    .header("Authorization", "Bearer " + accessToken())
                    .bodyValue(Collections.emptyMap())
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (WebClientResponseException exception) {
            throw googlePlayFailure("subscriptions.acknowledge", exception);
        }
    }

    private WebClient client() {
        if (properties.getPackageName() == null || properties.getPackageName().isBlank()) {
            throw new IllegalStateException("GOOGLE_PLAY_PACKAGE_NAME is not configured");
        }
        return webClientBuilder.baseUrl(API_BASE_URL).build();
    }

    private String accessToken() {
        try {
            GoogleCredential credential = GoogleCredential.getApplicationDefault()
                    .createScoped(Collections.singleton(ANDROID_PUBLISHER_SCOPE));
            credential.refreshToken();
            return credential.getAccessToken();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not obtain Google Play API credentials", exception);
        }
    }

    private ResponseStatusException googlePlayFailure(
            String operation,
            WebClientResponseException exception
    ) {
        int status = exception.getStatusCode().value();
        String response = exception.getResponseBodyAsString()
                .replace('\n', ' ')
                .replace('\r', ' ');
        if (response.length() > 500) {
            response = response.substring(0, 500);
        }
        log.error("Google Play {} failed with status {}: {}", operation, status, response);
        if (status == 400 || status == 404) {
            return new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Google Play could not find this purchase"
            );
        }
        if (status == 401 || status == 403) {
            return new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Google Play API credentials do not have purchase access"
            );
        }
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "Google Play purchase verification is unavailable"
        );
    }

    private Instant parseInstant(JsonNode node) {
        return node == null || node.isMissingNode() || node.asText().isBlank()
                ? null : Instant.parse(node.asText());
    }

    private String nullableText(JsonNode node) {
        return node == null || node.isMissingNode() || node.asText().isBlank()
                ? null : node.asText();
    }
}
