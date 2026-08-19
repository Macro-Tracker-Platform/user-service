package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GooglePurchaseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoogleRtdnRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RestoreGooglePurchasesRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.SubscriptionService;
import com.olehprukhnytskyi.util.CustomHeaders;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SubscriptionController {
    private static final String APP_VERSION_CODE_HEADER = "X-App-Version-Code";

    private final SubscriptionService subscriptionService;

    @PostMapping("/api/subscriptions/google/verify")
    public ResponseEntity<EntitlementResponseDto> verify(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode,
            @RequestBody @Valid GooglePurchaseDto purchase) {
        return ResponseEntity.ok(subscriptionService.verify(userId, purchase, appVersionCode));
    }

    @PostMapping("/api/subscriptions/google/restore")
    public ResponseEntity<EntitlementResponseDto> restore(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode,
            @RequestBody @Valid RestoreGooglePurchasesRequestDto request) {
        return ResponseEntity.ok(subscriptionService.restore(
                userId, request.getPurchases(), appVersionCode));
    }

    @GetMapping("/api/users/me/entitlements")
    public ResponseEntity<EntitlementResponseDto> entitlement(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = APP_VERSION_CODE_HEADER, required = false)
            String appVersionCode) {
        return ResponseEntity.ok(subscriptionService.getEntitlement(userId, appVersionCode));
    }

    @PostMapping("/internal/google-play/rtdn")
    public ResponseEntity<Void> rtdn(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody GoogleRtdnRequestDto request) {
        subscriptionService.verifyRtdnAuthorization(authorization);
        subscriptionService.processRtdn(request);
        return ResponseEntity.noContent().build();
    }
}
