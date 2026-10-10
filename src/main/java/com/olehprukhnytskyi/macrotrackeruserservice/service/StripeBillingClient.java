package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebPriceDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Component
public class StripeBillingClient {
    public static final String API_VERSION = "2024-06-20";
    private final WebBillingProperties properties;
    private final RestClient client;

    public StripeBillingClient(WebBillingProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(10000);
        client = builder.clone().baseUrl("https://api.stripe.com/v1")
                .requestFactory(factory).build();
    }

    public void validatePrice(String id, WebBillingPlan plan) {
        price(id, plan);
    }

    public WebPriceDto price(String id, WebBillingPlan plan) {
        JsonNode price = get("/prices/{id}", id);
        String interval = plan == WebBillingPlan.YEARLY ? "year" : "month";
        if (price == null || !price.path("active").asBoolean()
                || !price.path("livemode").isBoolean()
                || price.path("livemode").asBoolean() != properties.isLiveMode()
                || !interval.equals(price.path("recurring").path("interval").asText())
                || price.path("recurring").path("interval_count").asInt() != 1
                || !"per_unit".equals(price.path("billing_scheme").asText())
                || !price.path("unit_amount").isIntegralNumber()
                || price.path("unit_amount").asLong() <= 0
                || !price.path("currency").asText().matches("[a-z]{3}")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "WEB_PRICE_NOT_CONFIGURED");
        }
        String currency = price.path("currency").asText();
        int digits;
        try {
            digits = java.util.Currency.getInstance(currency.toUpperCase(java.util.Locale.ROOT))
                    .getDefaultFractionDigits();
            // Stripe represents ISK/UGX charges in two decimal places for compatibility.
            if ("isk".equals(currency) || "ugx".equals(currency)) {
                digits = 2;
            }
            if (digits < 0) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw unavailable();
        }
        return new WebPriceDto(plan.name(), price.path("unit_amount").asLong(), currency, digits);
    }

    public JsonNode createCheckout(WebCheckout checkout) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        if (checkout.getDiscountPercent() > 0) {
            MultiValueMap<String, String> coupon = new LinkedMultiValueMap<>();
            coupon.add("id", "macrotracker-" + checkout.getId());
            coupon.add("percent_off", Integer.toString(checkout.getDiscountPercent()));
            coupon.add("duration", "once");
            coupon.add("name", checkout.getPromoCode());
            JsonNode result = post("/coupons", coupon, "coupon-" + checkout.getId());
            form.add("discounts[0][coupon]", result.path("id").asText());
        }
        form.add("mode", "subscription");
        form.add("payment_method_types[0]", "card");
        form.add("line_items[0][price]", checkout.getPriceId());
        form.add("line_items[0][quantity]", "1");
        form.add("client_reference_id", checkout.getUserId().toString());
        form.add("metadata[checkout_id]", checkout.getId());
        form.add("subscription_data[metadata][checkout_id]", checkout.getId());
        form.add("success_url", checkout.getSuccessUrl());
        form.add("cancel_url", checkout.getCancelUrl());
        form.add("expires_at", Long.toString(checkout.getExpiresAt().getEpochSecond()));
        if (checkout.getTrialDays() > 0) {
            form.add("subscription_data[trial_period_days]",
                    Integer.toString(checkout.getTrialDays()));
        }
        return post("/checkout/sessions", form, "checkout-" + checkout.getId());
    }

    public JsonNode session(String id) {
        return get("/checkout/sessions/{id}", id);
    }

    public java.util.Optional<JsonNode> findSession(WebCheckout checkout) {
        String after = null;
        JsonNode found = null;
        // Search the entire bounded creation window; never infer expiration from a partial list.
        for (int page = 0; page < 10; page++) {
            final String cursor = after;
            JsonNode result;
            try {
                result = client.get().uri(builder -> {
                    builder.path("/checkout/sessions").queryParam("limit", 100)
                            .queryParam("created[gte]",
                                    checkout.getCreatedAt().minusSeconds(60).getEpochSecond())
                            .queryParam("created[lte]", checkout.getExpiresAt().getEpochSecond());
                    if (cursor != null) {
                        builder.queryParam("starting_after", cursor);
                    }
                    return builder.build();
                }).headers(headers -> headers.setBearerAuth(properties.getStripeSecretKey()))
                        .header("Stripe-Version", API_VERSION).retrieve().body(JsonNode.class);
            } catch (RuntimeException exception) {
                throw unavailable();
            }
            if (result == null || !result.path("data").isArray()
                    || !result.path("has_more").isBoolean()) {
                throw unavailable();
            }
            for (JsonNode session : result.path("data")) {
                if (checkout.getId().equals(
                        session.path("metadata").path("checkout_id").asText())) {
                    if (found != null && !found.path("id").asText()
                            .equals(session.path("id").asText())) {
                        throw unavailable();
                    }
                    found = session;
                }
                after = session.path("id").asText();
            }
            if (!result.path("has_more").asBoolean()) {
                return java.util.Optional.ofNullable(found);
            }
            if (after == null || !after.startsWith("cs_") || after.equals(cursor)) {
                throw unavailable();
            }
        }
        throw unavailable();
    }

    public JsonNode expireSession(String id) {
        return post("/checkout/sessions/" + id + "/expire", new LinkedMultiValueMap<>(),
                "delete-account-expire-" + id);
    }

    public JsonNode cancelSubscription(String id) {
        try {
            // Account deletion cancels immediately without a final invoice or proration refund.
            return client.delete().uri("/subscriptions/{id}?invoice_now=false&prorate=false", id)
                    .headers(headers -> headers.setBearerAuth(properties.getStripeSecretKey()))
                    .header("Stripe-Version", API_VERSION).retrieve().body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    public JsonNode invoice(String id) {
        return get("/invoices/{id}?expand[]=subscription", id);
    }

    public JsonNode subscription(String id) {
        return get("/subscriptions/{id}", id);
    }

    public JsonNode portal(String customerId, String returnUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerId);
        form.add("return_url", returnUrl);
        return post("/billing_portal/sessions", form, java.util.UUID.randomUUID().toString());
    }

    public JsonNode portalConfiguration(String id) {
        return get("/billing_portal/configurations/{id}", id);
    }

    public JsonNode mobilePortal(String customerId, String configurationId, String returnUrl) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerId);
        form.add("configuration", configurationId);
        form.add("return_url", returnUrl);
        return post("/billing_portal/sessions", form, java.util.UUID.randomUUID().toString());
    }

    private JsonNode get(String path, String id) {
        try {
            return client.get().uri(path, id)
                    .headers(headers -> headers.setBearerAuth(properties.getStripeSecretKey()))
                    .header("Stripe-Version", API_VERSION).retrieve().body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private JsonNode post(String path, MultiValueMap<String, String> form, String key) {
        try {
            return client.post().uri(path)
                    .headers(headers -> headers.setBearerAuth(properties.getStripeSecretKey()))
                    .header("Stripe-Version", API_VERSION).header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(JsonNode.class);
        } catch (RuntimeException exception) {
            throw unavailable();
        }
    }

    private ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "PAYMENT_PROVIDER_UNAVAILABLE");
    }
}
