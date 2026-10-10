package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class BillingProviderClientTest {
    @Test
    void deletionCancellationNeverCreatesFinalInvoiceOrProration() {
        var properties = new WebBillingProperties();
        properties.setStripeSecretKey("test_secret");
        var builder = RestClient.builder().baseUrl("https://api.stripe.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new StripeBillingClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("https://api.stripe.com/v1/subscriptions/sub_owned"
                    + "?invoice_now=false&prorate=false"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Stripe-Version", "2024-06-20"))
                .andRespond(withSuccess("{\"id\":\"sub_owned\",\"status\":\"canceled\"}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.cancelSubscription("sub_owned").path("status").asText())
                .isEqualTo("canceled");
        server.verify();
    }

    @Test
    void mobilePortalExplicitlyPassesConfigurationRatherThanStripeDefault() {
        var properties = new WebBillingProperties();
        properties.setStripeSecretKey("test_secret");
        var builder = RestClient.builder().baseUrl("https://api.stripe.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new StripeBillingClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("https://api.stripe.com/v1/billing_portal/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(allOf(containsString("customer=cus_owned"),
                        containsString("configuration=bpc_mobile"),
                        containsString("return_url=https%3A%2F%2Fmacrotracker.uk"))))
                .andRespond(withSuccess("{\"url\":\"https://billing.stripe.com/p/session/owned\"}",
                        MediaType.APPLICATION_JSON));
        client.mobilePortal("cus_owned", "bpc_mobile", "https://macrotracker.uk");
        server.verify();
    }

    @Test
    void freshRevenueCatAccessPreservesActualStoreEvenAfterAWebPurchase() {
        for (String store : java.util.List.of("stripe", "app_store", "play_store")) {
            var properties = new RevenueCatProperties();
            properties.setSubscriberApiKey("test_reader");
            var builder = RestClient.builder().baseUrl("https://api.revenuecat.com/v1");
            var server = MockRestServiceServer.bindTo(builder).build();
            var client = new RevenueCatApiClient(properties, RestClient.builder());
            ReflectionTestUtils.setField(client, "client", builder.build());
            String body = """
                    {"subscriber":{"first_seen":"2025-01-01T00:00:00Z",
                     "subscriptions":{"yearly":{"is_sandbox":false,"store":"%s"}},
                     "non_subscriptions":{},"entitlements":{
                     "macro_tracker_calorie_counter_premium":{"product_identifier":"yearly",
                     "expires_date":"2099-01-01T00:00:00Z"}}}}
                    """.formatted(store);
            server.expect(requestTo("https://api.revenuecat.com/v1/subscribers/42"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
            assertThat(client.customer(42L).store())
                    .isEqualTo(store.toUpperCase(java.util.Locale.ROOT));
            server.verify();
        }
    }

    @Test
    void incompleteRevenueCatResponseFailsClosed() {
        var properties = new RevenueCatProperties();
        properties.setSubscriberApiKey("test_reader");
        var builder = RestClient.builder().baseUrl("https://api.revenuecat.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RevenueCatApiClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("https://api.revenuecat.com/v1/subscribers/42"))
                .andExpect(header("Authorization", "Bearer test_reader"))
                .andRespond(withSuccess("{\"subscriber\":{}}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.customer(42L))
                .isInstanceOf(ResponseStatusException.class);
        server.verify();
    }

    @Test
    void freshRevenueCatHistorySeparatesActiveAccessFromTrialHistory() {
        var properties = new RevenueCatProperties();
        properties.setSubscriberApiKey("test_reader");
        var builder = RestClient.builder().baseUrl("https://api.revenuecat.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RevenueCatApiClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        String body = """
                {"subscriber":{"first_seen":"2025-01-01T00:00:00Z",
                  "subscriptions":{"old_product":{}},"non_subscriptions":{},"entitlements":{}}}
                """;
        server.expect(requestTo("https://api.revenuecat.com/v1/subscribers/42"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        var snapshot = client.customer(42L);
        assertThat(snapshot.active()).isFalse();
        assertThat(snapshot.hasPurchaseHistory()).isTrue();
        server.verify();
    }

    @Test
    void providerAuthenticationFailureNeverMeansEligible() {
        var properties = new RevenueCatProperties();
        properties.setSubscriberApiKey("test_reader");
        var builder = RestClient.builder().baseUrl("https://api.revenuecat.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RevenueCatApiClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("https://api.revenuecat.com/v1/subscribers/42"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withUnauthorizedRequest());
        assertThatThrownBy(() -> client.customer(42L))
                .isInstanceOf(ResponseStatusException.class);
        server.verify();
    }

    @Test
    void activeSandboxEntitlementCannotGrantProductionWebAccess() {
        var properties = new RevenueCatProperties();
        properties.setSubscriberApiKey("test_reader");
        var builder = RestClient.builder().baseUrl("https://api.revenuecat.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RevenueCatApiClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        String body = """
                {"subscriber":{"first_seen":"2025-01-01T00:00:00Z",
                 "subscriptions":{"yearly":{"is_sandbox":true}},"non_subscriptions":{},
                 "entitlements":{"macro_tracker_calorie_counter_premium":{
                   "product_identifier":"yearly","expires_date":"2099-01-01T00:00:00Z"}}}}
                """;
        server.expect(requestTo("https://api.revenuecat.com/v1/subscribers/42"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.customer(42L))
                .isInstanceOf(ResponseStatusException.class);
        server.verify();
    }

    @Test
    void stripeReceiptUsesSameAppUserIdAndStripeAppKey() {
        var properties = new RevenueCatProperties();
        properties.setStripeApiKey("test_stripe_app_key");
        var builder = RestClient.builder().baseUrl("https://api.revenuecat.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new RevenueCatApiClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("https://api.revenuecat.com/v1/receipts"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test_stripe_app_key"))
                .andExpect(header("X-Platform", "stripe"))
                .andExpect(content().json("{\"app_user_id\":\"42\",\"fetch_token\":\"sub_paid\"}"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        client.importStripeSubscription(42L, "sub_paid");
        server.verify();
    }

    @Test
    void checkoutUsesFrozenPriceTrialDiscountAndStableIdempotencyKeys() {
        var properties = new WebBillingProperties();
        properties.setStripeSecretKey("test_secret");
        var builder = RestClient.builder().baseUrl("https://api.stripe.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new StripeBillingClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(client, "client", builder.build());
        server.expect(requestTo("https://api.stripe.com/v1/coupons"))
                .andExpect(header("Idempotency-Key", "coupon-attempt"))
                .andExpect(content().string(allOf(containsString("percent_off=15"),
                        containsString("duration=once"))))
                .andRespond(withSuccess("{\"id\":\"coupon_owned\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.stripe.com/v1/checkout/sessions"))
                .andExpect(header("Stripe-Version", "2024-06-20"))
                .andExpect(header("Authorization", "Bearer test_secret"))
                .andExpect(header("Idempotency-Key", "checkout-attempt"))
                .andExpect(content().string(allOf(containsString("mode=subscription"),
                        containsString("line_items%5B0%5D%5Bprice%5D=price_year"),
                        containsString("discounts%5B0%5D%5Bcoupon%5D=coupon_owned"),
                        containsString("subscription_data%5Btrial_period_days%5D=7"),
                        containsString("client_reference_id=42"))))
                .andRespond(withSuccess("{\"id\":\"cs_owned\"}", MediaType.APPLICATION_JSON));
        WebCheckout checkout = WebCheckout.builder().id("attempt").userId(42L)
                .discountPercent(15).promoCode("PARTNER").priceId("price_year").trialDays(7)
                .successUrl("https://example.com/success").cancelUrl("https://example.com/cancel")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        assertThat(client.createCheckout(checkout).path("id").asText()).isEqualTo("cs_owned");
        server.verify();
    }
}
