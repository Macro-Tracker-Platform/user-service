package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCheckoutRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebSubscriptionEligibilityDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebAffiliate;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebCheckout;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.RevenueCatProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.WebBillingProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WebCheckoutState;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebCheckoutReservationService {
    private static final List<WebCheckoutState> PENDING = List.of(
            WebCheckoutState.CREATING, WebCheckoutState.OPEN);
    private static final List<WebCheckoutState> RESERVED = List.of(
            WebCheckoutState.CREATING, WebCheckoutState.OPEN, WebCheckoutState.COMPLETED);
    private final WebBillingProperties properties;
    private final RevenueCatProperties revenueCatProperties;
    private final UserRepository userRepository;
    private final WebCheckoutRepository checkoutRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeService promoCodeService;
    private final WebSubscriptionEligibilityService eligibilityService;
    private final RevenueCatApiClient revenueCat;
    private final StripeBillingClient stripe;

    @Transactional
    public WebCheckout prepare(Long userId, String roles, WebCheckoutRequestDto request) {
        requireConfigured();
        userRepository.findByIdForUpdate(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        String code = normalize(request.code());
        WebCheckout previous = checkoutRepository
                .findFirstByUserIdAndStateInOrderByCreatedAtDesc(userId, PENDING).orElse(null);
        if (previous != null) {
            if (previous.getState() == WebCheckoutState.OPEN) {
                applySession(previous, stripe.session(previous.getStripeSessionId()));
            }
            if (previous.getState() != WebCheckoutState.EXPIRED) {
                if (previous.getPlan() != request.plan()
                        || !normalize(previous.getPromoCode()).equals(code)) {
                    throw conflict("CHECKOUT_ALREADY_PENDING");
                }
                if (previous.getState() == WebCheckoutState.CREATING
                        && previous.getExpiresAt().isBefore(Instant.now().plusSeconds(1800))) {
                    // An uncertain provider response must never create a second payment session.
                    throw conflict("CHECKOUT_RECONCILIATION_REQUIRED");
                }
                return previous;
            }
        }
        WebSubscriptionEligibilityDto eligibility = eligibilityService.evaluateForCheckout(
                userId, roles, revenueCat.customer(userId));
        if (!eligibility.checkoutEligible()) {
            throw conflict(eligibility.reason());
        }
        String priceId = request.plan() == WebBillingPlan.YEARLY
                ? properties.getYearlyPriceId() : properties.getMonthlyPriceId();
        stripe.validatePrice(priceId, request.plan());
        PromoCode promo = reserveCode(userId, code, eligibility);
        Instant now = Instant.now();
        boolean previousCheckout = checkoutRepository.existsByUserIdAndState(
                userId, WebCheckoutState.COMPLETED);
        String checkoutId = UUID.randomUUID().toString();
        String successUrl = org.springframework.web.util.UriComponentsBuilder
                .fromUriString(properties.getSuccessUrl()).replaceQueryParam("checkout", checkoutId)
                .build().toUriString();
        WebCheckout checkout = WebCheckout.builder().id(checkoutId)
                .userId(userId).plan(request.plan()).state(WebCheckoutState.CREATING)
                .priceId(priceId)
                .successUrl(successUrl).cancelUrl(properties.getCancelUrl())
                .createdAt(now).expiresAt(now.plus(properties.getCheckoutLifetime()))
                .acquisitionType(PromoAcquisitionType.DIRECT)
                .commissionPercent(BigDecimal.ZERO).referrerCommissionPercent(BigDecimal.ZERO)
                .trialDays(request.plan() == WebBillingPlan.YEARLY && eligibility.trialEligible()
                        && !previousCheckout ? properties.getYearlyTrialDays() : 0).build();
        if (promo != null) {
            checkout.setPromoCodeId(promo.getId());
            checkout.setPromoCode(promo.getCode());
            checkout.setDiscountPercent(promo.getDiscountPercent());
            snapshotAffiliate(checkout, promo);
        }
        return checkoutRepository.save(checkout);
    }

    @Transactional
    public WebCheckout createSession(String id) {
        Long userId = checkoutRepository.findUserIdById(id).orElseThrow();
        // Share the account lock with deletion so no new payment session survives deletion.
        userRepository.findByIdForUpdate(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND"));
        WebCheckout checkout = checkoutRepository.findByIdForUpdate(id).orElseThrow();
        if (checkout.getState() == WebCheckoutState.CREATING) {
            applySession(checkout, stripe.createCheckout(checkout));
        }
        return checkout;
    }

    @Transactional
    public WebCheckout attach(String id, JsonNode session) {
        WebCheckout checkout = checkoutRepository.findByIdForUpdate(id).orElseThrow();
        if (checkout.getAccountDeletedAt() == null) {
            applySession(checkout, session);
        }
        return checkout;
    }

    void applySession(WebCheckout checkout, JsonNode session) {
        if (session == null || !session.path("id").asText().startsWith("cs_")
                || !session.path("livemode").isBoolean()
                || session.path("livemode").asBoolean() != properties.isLiveMode()
                || !session.path("metadata").path("checkout_id").asText().equals(checkout.getId())
                || !session.path("client_reference_id").asText()
                    .equals(checkout.getUserId().toString())
                || !"subscription".equals(session.path("mode").asText())
                || (checkout.getStripeSessionId() != null
                    && !checkout.getStripeSessionId().equals(session.path("id").asText()))) {
            throw conflict("CHECKOUT_PROVIDER_MISMATCH");
        }
        checkout.setStripeSessionId(session.path("id").asText());
        String status = session.path("status").asText();
        if ("complete".equals(status)) {
            String subscription = session.path("subscription").asText();
            if (!subscription.startsWith("sub_")) {
                throw conflict("CHECKOUT_SUBSCRIPTION_MISSING");
            }
            bindSubscription(checkout, subscription);
        } else if (checkout.getState() != WebCheckoutState.COMPLETED) {
            if ("expired".equals(status)) {
                checkout.setState(WebCheckoutState.EXPIRED);
            } else if ("open".equals(status)) {
                checkout.setState(WebCheckoutState.OPEN);
                checkout.setCheckoutUrl(session.path("url").asText());
            } else {
                throw conflict("CHECKOUT_PROVIDER_MISMATCH");
            }
        }
    }

    void bindSubscription(WebCheckout checkout, String subscriptionId) {
        if (checkout.getStripeSubscriptionId() != null
                && !checkout.getStripeSubscriptionId().equals(subscriptionId)) {
            throw conflict("CHECKOUT_SUBSCRIPTION_MISMATCH");
        }
        checkout.setStripeSubscriptionId(subscriptionId);
        checkout.setState(WebCheckoutState.COMPLETED);
        checkout.setCheckoutUrl(null);
        checkout.setReceiptSyncedAt(null);
        checkout.setReceiptSyncRequestedAt(Instant.now());
    }

    private PromoCode reserveCode(Long userId, String code,
                                  WebSubscriptionEligibilityDto eligibility) {
        if (code.isEmpty()) {
            return null;
        }
        PromoCode found = promoCodeRepository.findByCodeIgnoreCase(code).orElseThrow(() ->
                conflict("PROMO_CODE_INVALID"));
        final PromoCode promo = promoCodeRepository.findByIdForUpdate(found.getId()).orElseThrow();
        PromoCodeRequestDto preview = new PromoCodeRequestDto();
        preview.setCode(code);
        promoCodeService.validateWebCode(userId, preview, eligibility);
        if (checkoutRepository.existsByUserIdAndStateAndPromoCodeIdIsNotNull(
                userId, WebCheckoutState.COMPLETED)) {
            throw conflict("PROMO_CODE_ALREADY_USED");
        }
        if (promo.getMaxRedemptions() != null
                && promoCodeService.consumedCount(promo.getId())
                    + checkoutRepository.countByPromoCodeIdAndStateIn(promo.getId(), RESERVED)
                        >= promo.getMaxRedemptions()) {
            throw conflict("PROMO_CODE_EXHAUSTED");
        }
        return promo;
    }

    private void snapshotAffiliate(WebCheckout checkout, PromoCode promo) {
        checkout.setAcquisitionType(promo.getAcquisitionType());
        if (promo.getAcquisitionType() != PromoAcquisitionType.AFFILIATE) {
            return;
        }
        WebAffiliate affiliate = promo.getWebAffiliate();
        if (affiliate == null || !affiliate.isActive()) {
            throw conflict("AFFILIATE_UNAVAILABLE");
        }
        checkout.setAffiliateId(affiliate.getId());
        checkout.setPartnerName(affiliate.getName());
        checkout.setCommissionPercent(WebCommissionService.AFFILIATE_PERCENT);
        WebAffiliate referrer = affiliate.getReferrer();
        if (referrer != null) {
            if (!referrer.isActive() || referrer.getId().equals(affiliate.getId())) {
                throw conflict("REFERRER_UNAVAILABLE");
            }
            checkout.setReferrerId(referrer.getId());
            checkout.setReferrerName(referrer.getName());
            checkout.setReferrerCommissionPercent(WebCommissionService.REFERRER_PERCENT);
        }
    }

    void requireConfigured() {
        if (!properties.isEnabled() || blank(properties.getStripeSecretKey())
                || blank(properties.getStripeWebhookSecret())
                || blank(properties.getMonthlyPriceId()) || blank(properties.getYearlyPriceId())
                || blank(revenueCatProperties.getSubscriberApiKey())
                || blank(revenueCatProperties.getStripeApiKey())
                || !https(properties.getSuccessUrl()) || !https(properties.getCancelUrl())
                || !https(properties.getPortalReturnUrl())
                || properties.getYearlyTrialDays() < 0 || properties.getYearlyTrialDays() > 30
                || properties.getCheckoutLifetime() == null
                || properties.getCheckoutLifetime().compareTo(Duration.ofMinutes(35)) < 0
                || properties.getCheckoutLifetime().compareTo(Duration.ofHours(24)) > 0
                || !(properties.isLiveMode() ? "PRODUCTION" : "SANDBOX")
                    .equals(revenueCatProperties.getEnvironment())) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "WEB_CHECKOUT_NOT_CONFIGURED");
        }
    }

    private boolean https(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equals(uri.getScheme()) && uri.getHost() != null;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
