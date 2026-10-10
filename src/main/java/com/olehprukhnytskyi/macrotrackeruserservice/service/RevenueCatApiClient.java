package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatCustomerSnapshot;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class RevenueCatApiClient {
    private final RevenueCatProperties properties;
    private final RestClient client;

    public RevenueCatApiClient(RevenueCatProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        client = builder.clone().baseUrl("https://api.revenuecat.com/v1")
                .requestFactory(factory).build();
    }

    public RevenueCatCustomerSnapshot customer(Long userId) {
        requireKey(properties.getSubscriberApiKey());
        try {
            JsonNode response = client.get().uri("/subscribers/{id}", userId)
                    .headers(headers -> headers.setBearerAuth(properties.getSubscriberApiKey()))
                    .retrieve().body(JsonNode.class);
            if (response == null || !response.path("subscriber").isObject()) {
                throw unavailable();
            }
            JsonNode subscriber = response.path("subscriber");
            if (!subscriber.path("subscriptions").isObject()
                    || !subscriber.path("non_subscriptions").isObject()
                    || !subscriber.path("entitlements").isObject()
                    || !subscriber.path("first_seen").isTextual()) {
                throw unavailable();
            }
            JsonNode entitlement = subscriber.path("entitlements")
                    .path(properties.getEntitlementId());
            Instant expires = date(entitlement.path("expires_date"));
            Instant grace = date(entitlement.path("grace_period_expires_date"));
            if (grace != null && (expires == null || grace.isAfter(expires))) {
                expires = grace;
            }
            boolean active = entitlement.isObject()
                    && (expires == null || expires.isAfter(Instant.now()));
            if (active) {
                JsonNode source = subscriber.path("subscriptions")
                        .path(entitlement.path("product_identifier").asText());
                boolean sandbox = "SANDBOX".equals(properties.getEnvironment());
                if (!source.path("is_sandbox").isBoolean()
                        || source.path("is_sandbox").asBoolean() != sandbox) {
                    // A mixed sandbox/production profile needs explicit reconciliation.
                    throw unavailable();
                }
            }
            String store = null;
            if (active) {
                String sourceStore = subscriber.path("subscriptions")
                        .path(entitlement.path("product_identifier").asText())
                        .path("store").asText();
                store = switch (sourceStore) {
                    case "app_store", "mac_app_store" -> "APP_STORE";
                    case "play_store" -> "PLAY_STORE";
                    case "stripe" -> "STRIPE";
                    default -> null;
                };
            }
            boolean history = !subscriber.path("subscriptions").isEmpty()
                    || !subscriber.path("non_subscriptions").isEmpty()
                    || !subscriber.path("entitlements").isEmpty();
            return new RevenueCatCustomerSnapshot(active, history, expires, store);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public void importStripeSubscription(Long userId, String subscriptionId) {
        requireKey(properties.getStripeApiKey());
        try {
            client.post().uri("/receipts")
                    .headers(headers -> headers.setBearerAuth(properties.getStripeApiKey()))
                    .header("X-Platform", "stripe").contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("app_user_id", userId.toString(), "fetch_token", subscriptionId))
                    .retrieve().toBodilessEntity();
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private Instant date(JsonNode value) {
        return value.isTextual() ? Instant.parse(value.asText()) : null;
    }

    private void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "BILLING_RECONCILIATION_UNAVAILABLE");
    }
}
