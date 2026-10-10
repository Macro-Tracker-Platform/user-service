package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCatalogDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCheckoutRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCheckoutResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebPromoCodeResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebSubscriptionEligibilityDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.PromoCodeService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.WebCheckoutService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.WebStorefrontService;
import com.olehprukhnytskyi.util.CustomHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions/web")
public class WebSubscriptionController {
    private final WebStorefrontService storefront;
    private final PromoCodeService promoCodeService;
    private final WebCheckoutService checkoutService;

    @PostMapping("/checkouts")
    public WebCheckoutResponseDto createCheckout(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = CustomHeaders.X_USER_ROLES, required = false) String roles,
            @RequestBody @Valid WebCheckoutRequestDto request) {
        return checkoutService.create(userId, roles, request);
    }

    @GetMapping("/checkouts/{id}")
    public WebCheckoutResponseDto checkoutStatus(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId, @PathVariable String id) {
        return checkoutService.status(userId, id);
    }

    @GetMapping("/eligibility")
    public WebSubscriptionEligibilityDto eligibility(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = CustomHeaders.X_USER_ROLES, required = false) String roles) {
        return storefront.eligibility(userId, roles);
    }

    @PostMapping("/promo-codes/validate")
    public WebPromoCodeResponseDto validatePromoCode(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = CustomHeaders.X_USER_ROLES, required = false) String roles,
            @RequestBody @Valid PromoCodeRequestDto request) {
        return promoCodeService.validateWebCode(userId, request,
                storefront.eligibility(userId, roles));
    }

    @GetMapping("/catalog")
    public WebCatalogDto catalog(@RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = CustomHeaders.X_USER_ROLES, required = false) String roles) {
        return storefront.catalog(userId, roles);
    }

    @GetMapping("/account")
    public com.olehprukhnytskyi.macrotrackeruserservice.dto.WebAccountDto account(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = CustomHeaders.X_USER_ROLES, required = false) String roles) {
        return storefront.account(userId, roles);
    }

    @PostMapping("/portal")
    public java.util.Map<String, String> portal(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return storefront.portal(userId);
    }

    @PostMapping("/mobile-portal")
    public java.util.Map<String, String> mobilePortal(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return storefront.mobilePortal(userId);
    }
}
