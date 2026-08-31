package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.FoodPhotoScanConsumption;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.FoodPhotoScanConsumptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
@RequiredArgsConstructor
public class FoodPhotoScanCreditService {
    static final String CACHE_PREFIX = "scans:free:credits:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final FoodPhotoScanConsumptionRepository consumptionRepository;

    @Transactional(readOnly = true)
    public FoodPhotoScanCreditDto getRemaining(Long userId) {
        String cached = redisTemplate.opsForValue().get(cacheKey(userId));
        Integer cachedCredits = parseCredits(cached);
        int credits = cachedCredits == null
                ? userRepository.findById(userId)
                        .map(User::getFoodPhotoScanCredits)
                        .orElseThrow(() -> new IllegalArgumentException("User not found"))
                : cachedCredits;
        if (cachedCredits == null) {
            redisTemplate.opsForValue().set(cacheKey(userId), String.valueOf(credits), CACHE_TTL);
        }
        return response(credits, false);
    }

    @Transactional
    public FoodPhotoScanCreditDto consume(Long userId) {
        return consume(userId, null);
    }

    @Transactional
    public FoodPhotoScanCreditDto consume(Long userId, String idempotencyKey) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null && consumptionRepository
                .existsByUserIdAndIdempotencyKey(userId, normalizedKey)) {
            return response(user.getFoodPhotoScanCredits(), true);
        }
        if (user.getFoodPhotoScanCredits() <= 0) {
            invalidateAfterCommit(userId);
            return response(0, false);
        }
        user.setFoodPhotoScanCredits(user.getFoodPhotoScanCredits() - 1);
        userRepository.save(user);
        if (normalizedKey != null) {
            consumptionRepository.save(
                    new FoodPhotoScanConsumption(userId, normalizedKey));
        }
        invalidateAfterCommit(userId);
        return response(user.getFoodPhotoScanCredits(), true);
    }

    private String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("Idempotency-Key must not exceed 128 characters");
        }
        return normalized;
    }

    private FoodPhotoScanCreditDto response(int credits, boolean consumed) {
        return FoodPhotoScanCreditDto.builder()
                .allowed(credits > 0)
                .consumed(consumed)
                .remainingScans(Math.max(0, credits))
                .build();
    }

    private void invalidateAfterCommit(Long userId) {
        Runnable invalidation = () -> {
            try {
                redisTemplate.delete(cacheKey(userId));
            } catch (RuntimeException exception) {
                log.warn("Could not invalidate food photo credit cache for userId={}",
                        userId, exception);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            invalidation.run();
                        }
                    });
        } else {
            invalidation.run();
        }
    }

    private String cacheKey(Long userId) {
        return CACHE_PREFIX + userId;
    }

    private Integer parseCredits(String value) {
        try {
            return value == null ? null : Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
