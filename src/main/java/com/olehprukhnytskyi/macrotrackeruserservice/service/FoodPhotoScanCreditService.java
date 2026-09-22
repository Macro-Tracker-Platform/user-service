package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FoodPhotoScanCreditService {
    private static final String TOKEN_PREFIX = "food-photo:";
    private final AiCreditService aiCreditService;

    public FoodPhotoScanCreditDto getRemaining(Long userId) {
        return aiCreditService.getRemaining(userId);
    }

    public FoodPhotoScanCreditDto consume(Long userId) {
        return consume(userId, null);
    }

    public FoodPhotoScanCreditDto consume(Long userId, String idempotencyKey) {
        return aiCreditService.consume(userId, TOKEN_PREFIX + normalize(idempotencyKey));
    }

    private String normalize(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank()
                ? java.util.UUID.randomUUID().toString()
                : idempotencyKey.trim();
    }
}
