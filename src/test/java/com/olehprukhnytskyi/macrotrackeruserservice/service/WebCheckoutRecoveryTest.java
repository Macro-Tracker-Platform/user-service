package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class WebCheckoutRecoveryTest {
    @Test
    void recoveryFindsOwnedSessionAcrossPagesUsingBoundedCreationWindow() {
        var builder = RestClient.builder().baseUrl("https://api.stripe.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var stripe = stripe(builder);
        server.expect(request -> {
            assertThat(request.getURI().getPath()).isEqualTo("/v1/checkout/sessions");
            assertThat(request.getURI().getQuery()).contains("limit=100", "created[gte]",
                    "created[lte]");
        }).andRespond(withSuccess("""
                {"data":[{"id":"cs_other","metadata":{"checkout_id":"other"}}],
                 "has_more":true}
                """, MediaType.APPLICATION_JSON));
        server.expect(request -> assertThat(request.getURI().getQuery())
                .contains("starting_after=cs_other"))
                .andRespond(withSuccess("""
                    {"data":[{"id":"cs_owned","metadata":{"checkout_id":"owned"}}],
                     "has_more":false}
                    """, MediaType.APPLICATION_JSON));
        assertThat(stripe.findSession(checkout()).orElseThrow().path("id").asText())
                .isEqualTo("cs_owned");
        server.verify();
    }

    @Test
    void emptyProviderHistoryDoesNotProveUnknownAttemptExpired() {
        var repository = mock(WebCheckoutRepository.class);
        var reservations = mock(WebCheckoutReservationService.class);
        var stripe = mock(StripeBillingClient.class);
        var checkout = checkout();
        when(repository.findByIdAndUserId("owned", 42L)).thenReturn(Optional.of(checkout));
        when(stripe.findSession(checkout)).thenReturn(Optional.empty());
        var response = new WebCheckoutService(reservations, repository, stripe)
                .status(42L, "owned");
        assertThat(response.state()).isEqualTo("CREATING");
        verify(stripe, never()).createCheckout(checkout);
        verifyNoInteractions(reservations);
    }

    @Test
    void foundSessionUsesOwnershipValidationWithoutNewPayment() throws Exception {
        var repository = mock(WebCheckoutRepository.class);
        final var reservations = mock(WebCheckoutReservationService.class);
        var stripe = mock(StripeBillingClient.class);
        var checkout = checkout();
        var session = new ObjectMapper().readTree("{\"id\":\"cs_owned\"}");
        when(repository.findByIdAndUserId("owned", 42L)).thenReturn(Optional.of(checkout));
        when(stripe.findSession(checkout)).thenReturn(Optional.of(session));
        var recovered = checkout();
        recovered.setState(WebCheckoutState.COMPLETED);
        when(reservations.attach("owned", session)).thenReturn(recovered);
        assertThat(new WebCheckoutService(reservations, repository, stripe).status(42L, "owned")
                .state()).isEqualTo("COMPLETED");
        verify(reservations).attach("owned", session);
        verify(stripe, never()).createCheckout(checkout);
    }

    @Test
    void anotherAccountCannotRecoverOrInspectAttempt() {
        var stripe = mock(StripeBillingClient.class);
        var service = new WebCheckoutService(mock(WebCheckoutReservationService.class),
                mock(WebCheckoutRepository.class), stripe);
        assertThatThrownBy(() -> service.status(99L, "owned"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("NOT_FOUND");
        verifyNoInteractions(stripe);
    }

    @Test
    void malformedPaginationNeverReturnsPartialResult() {
        var builder = RestClient.builder().baseUrl("https://api.stripe.com/v1");
        var server = MockRestServiceServer.bindTo(builder).build();
        var stripe = stripe(builder);
        server.expect(request -> assertThat(request.getURI().getPath())
                .isEqualTo("/v1/checkout/sessions"))
                .andRespond(withSuccess("{\"data\":[],\"has_more\":true}",
                        MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> stripe.findSession(checkout()))
                .isInstanceOf(ResponseStatusException.class);
    }

    private StripeBillingClient stripe(RestClient.Builder builder) {
        var properties = new WebBillingProperties();
        properties.setStripeSecretKey("test_secret");
        var stripe = new StripeBillingClient(properties, RestClient.builder());
        ReflectionTestUtils.setField(stripe, "client", builder.build());
        return stripe;
    }

    private WebCheckout checkout() {
        return WebCheckout.builder().id("owned").userId(42L).state(WebCheckoutState.CREATING)
                .createdAt(Instant.now().minusSeconds(3600))
                .expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
