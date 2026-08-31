package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.FoodPhotoScanCreditService;
import com.olehprukhnytskyi.util.CustomHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class FoodPhotoScanCreditController {
    private final FoodPhotoScanCreditService creditService;

    @GetMapping("/api/users/me/food-photo-scans/credits")
    public ResponseEntity<FoodPhotoScanCreditDto> getRemaining(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(creditService.getRemaining(userId));
    }

    @PostMapping("/api/users/me/food-photo-scans/consume")
    public ResponseEntity<FoodPhotoScanCreditDto> consume(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey) {
        return ResponseEntity.ok(creditService.consume(userId, idempotencyKey));
    }
}
