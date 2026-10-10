package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCheckoutRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCheckoutResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebCheckoutService {
    private final WebCheckoutReservationService reservations;
    private final WebCheckoutRepository repository;
    private final StripeBillingClient stripe;

    public WebCheckoutResponseDto create(Long userId, String roles, WebCheckoutRequestDto request) {
        WebCheckout checkout = reservations.prepare(userId, roles, request);
        if (checkout.getState() == WebCheckoutState.CREATING) {
            // Reservation commits before Stripe is called, including for timeouts/retries.
            checkout = reservations.createSession(checkout.getId());
        }
        return response(checkout);
    }

    public WebCheckoutResponseDto status(Long userId, String id) {
        WebCheckout checkout = repository.findByIdAndUserId(id, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "CHECKOUT_NOT_FOUND"));
        if (checkout.getAccountDeletedAt() != null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "CHECKOUT_NOT_FOUND");
        }
        if (checkout.getState() == WebCheckoutState.OPEN) {
            checkout = reservations.attach(id, stripe.session(checkout.getStripeSessionId()));
        } else if (checkout.getState() == WebCheckoutState.CREATING
                && checkout.getCreatedAt().isBefore(java.time.Instant.now().minusSeconds(30))) {
            var recovered = stripe.findSession(checkout);
            if (recovered.isPresent()) {
                checkout = reservations.attach(id, recovered.get());
            }
        }
        return response(checkout);
    }

    private WebCheckoutResponseDto response(WebCheckout checkout) {
        return new WebCheckoutResponseDto(checkout.getId(), checkout.getState().name(),
                checkout.getCheckoutUrl(), checkout.getExpiresAt());
    }
}
