package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.InsertAffiliateWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.InsertAffiliateWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class InsertAffiliateWebhookController {
    private final InsertAffiliateWebhookService webhookService;

    @PostMapping("/internal/insert-affiliate/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody InsertAffiliateWebhookDto payload) {
        webhookService.verifyAuthorization(authorization);
        webhookService.process(payload);
        return ResponseEntity.ok().build();
    }
}
