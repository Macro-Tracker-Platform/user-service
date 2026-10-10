package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebAccountDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCatalogDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCheckoutResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebSubscriptionEligibilityDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import com.olehprukhnytskyi.util.UserRole;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebStorefrontService {
    private final WebBillingProperties properties;
    private final WebCheckoutReservationService reservations;
    private final WebSubscriptionEligibilityService eligibilityService;
    private final RevenueCatApiClient revenueCat;
    private final StripeBillingClient stripe;
    private final WebCheckoutRepository checkouts;
    private final SubscriptionService subscriptions;
    private final UserRepository users;

    public WebAccountDto account(Long userId, String roles) {
        var user = users.findById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        var pending = checkouts.findFirstByUserIdAndStateInOrderByCreatedAtDesc(userId,
                List.of(WebCheckoutState.CREATING, WebCheckoutState.OPEN)).orElse(null);
        var pendingResponse = pending == null ? null : new WebCheckoutResponseDto(pending.getId(),
                pending.getState().name(), pending.getCheckoutUrl(), pending.getExpiresAt());
        return new WebAccountDto(user.getEmail(), subscriptions.getEntitlement(userId, roles),
                checkouts.existsByUserIdAndState(userId, WebCheckoutState.COMPLETED),
                pendingResponse, user.getRoles().contains(UserRole.ADMIN));
    }

    public WebCatalogDto catalog(Long userId, String roles) {
        if (!properties.isEnabled()) {
            return new WebCatalogDto(false, 0, List.of(), null);
        }
        reservations.requireConfigured();
        var eligibility = eligibility(userId, roles);
        return new WebCatalogDto(true, properties.getYearlyTrialDays(), List.of(
                stripe.price(properties.getMonthlyPriceId(), WebBillingPlan.MONTHLY),
                stripe.price(properties.getYearlyPriceId(), WebBillingPlan.YEARLY)), eligibility);
    }

    public WebSubscriptionEligibilityDto eligibility(Long userId, String roles) {
        if (!properties.isEnabled()) {
            return eligibilityService.evaluate(userId, roles);
        }
        var result = eligibilityService.evaluateForCheckout(userId, roles,
                revenueCat.customer(userId));
        boolean usedTrial = checkouts.existsByUserIdAndState(userId, WebCheckoutState.COMPLETED);
        return new WebSubscriptionEligibilityDto(result.checkoutEligible(),
                result.trialEligible() && !usedTrial, result.reconciliationRequired(),
                result.reason(), result.currentAccessUntil());
    }

    public Map<String, String> portal(Long userId) {
        return createPortal(userId, false);
    }

    public Map<String, String> mobilePortal(Long userId) {
        return createPortal(userId, true);
    }

    private Map<String, String> createPortal(Long userId, boolean mobile) {
        // Disabling new sales must not prevent existing customers from canceling.
        if (properties.getStripeSecretKey() == null
                || properties.getStripeSecretKey().isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "BILLING_PORTAL_NOT_CONFIGURED");
        }
        var checkout = checkouts.findFirstByUserIdAndStateInOrderByCreatedAtDesc(
                userId, List.of(WebCheckoutState.COMPLETED)).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND,
                            "WEB_SUBSCRIPTION_NOT_FOUND"));
        var subscription = stripe.subscription(checkout.getStripeSubscriptionId());
        String customer = subscription.path("customer").asText();
        if (!customer.startsWith("cus_") || !subscription.path("livemode").isBoolean()
                || subscription.path("livemode").asBoolean() != properties.isLiveMode()
                || !checkout.getStripeSubscriptionId().equals(subscription.path("id").asText())
                || !checkout.getId().equals(subscription.path("metadata")
                    .path("checkout_id").asText())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "BILLING_CUSTOMER_MISMATCH");
        }
        URI returnUri;
        try {
            returnUri = URI.create(mobile ? properties.getMobilePortalReturnUrl()
                    : properties.getPortalReturnUrl());
            if (!"https".equals(returnUri.getScheme()) || returnUri.getHost() == null
                    || (mobile && !"/subscription-managed.html".equals(returnUri.getPath()))) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "BILLING_PORTAL_NOT_CONFIGURED");
        }
        JsonNode session;
        if (mobile) {
            String configuration = properties.getMobilePortalConfigurationId();
            requireMobilePortalConfiguration(configuration);
            session = stripe.mobilePortal(customer, configuration, returnUri.toString());
        } else {
            session = stripe.portal(customer, returnUri.toString());
        }
        String url = session.path("url").asText();
        if (!url.startsWith("https://billing.stripe.com/")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "BILLING_PORTAL_UNAVAILABLE");
        }
        return Map.of("url", url);
    }

    private void requireMobilePortalConfiguration(String id) {
        if (id == null || !id.startsWith("bpc_")) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MOBILE_BILLING_PORTAL_NOT_CONFIGURED");
        }
        JsonNode configuration = stripe.portalConfiguration(id);
        JsonNode features = configuration.path("features");
        JsonNode cancel = features.path("subscription_cancel");
        if (!id.equals(configuration.path("id").asText())
                || !configuration.path("active").asBoolean()
                || !configuration.path("livemode").isBoolean()
                || configuration.path("livemode").asBoolean() != properties.isLiveMode()
                || !cancel.path("enabled").asBoolean()
                || !"at_period_end".equals(cancel.path("mode").asText())
                || !"none".equals(cancel.path("proration_behavior").asText())
                || (cancel.hasNonNull("retention")
                    && !cancel.path("retention").isEmpty())
                || !disabled(features.path("subscription_update"))
                || !disabled(features.path("payment_method_update"))
                || !disabled(features.path("customer_update"))
                || features.path("subscription_pause").path("enabled").asBoolean()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "MOBILE_BILLING_PORTAL_UNSAFE_CONFIGURATION");
        }
    }

    private boolean disabled(JsonNode feature) {
        return feature.path("enabled").isBoolean() && !feature.path("enabled").asBoolean();
    }
}
