package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.RevenueCatWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.RevenueCatWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class RevenueCatWebhookController {
    private final RevenueCatWebhookService webhookService;

    @PostMapping("/internal/revenuecat/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody RevenueCatWebhookDto payload) {
        webhookService.verifyAuthorization(authorization);
        webhookService.process(payload);
        return ResponseEntity.ok().build();
    }
}
