package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.AiCreditProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCreditService {
    static final String DAILY_PREFIX = "ai-credits:free:daily:";
    static final String COMPLETED_PREFIX = "ai-credits:free:completed:";
    static final String EXHAUSTED_PREFIX = "ai-credits:free:exhausted:";
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT =
            new DefaultRedisScript<>("""
                    local used = tonumber(redis.call('GET', KEYS[1]) or '0')
                    if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then return used end
                    if used >= tonumber(ARGV[2]) then return -1 end
                    used = redis.call('INCR', KEYS[1])
                    redis.call('EXPIRE', KEYS[1], ARGV[3])
                    redis.call('SADD', KEYS[2], ARGV[1])
                    redis.call('EXPIRE', KEYS[2], ARGV[3])
                    return used
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AiCreditProperties properties;

    public FoodPhotoScanCreditDto getRemaining(Long userId) {
        Window window = window(userId);
        String rawUsed = redisTemplate.opsForValue().get(window.usageKey());
        int used = parseUsage(rawUsed);
        return response(window, Math.max(0, window.limit() - used), false);
    }

    public FoodPhotoScanCreditDto consume(Long userId, String idempotencyKey) {
        Window window = window(userId);
        String token = normalizeToken(idempotencyKey);
        Long used = redisTemplate.execute(
                CONSUME_SCRIPT,
                List.of(window.usageKey(), window.completedKey()),
                token,
                String.valueOf(window.limit()),
                String.valueOf(window.ttlSeconds()));
        if (used == null) {
            throw new IllegalStateException("Could not consume AI credit");
        }
        if (used < 0) {
            return response(window, 0, false);
        }
        if (used >= window.limit()) {
            markExhausted(userId, window.date());
        }
        return response(window, Math.max(0, window.limit() - used.intValue()), true);
    }

    public boolean wasExhausted(Long userId, java.time.LocalDate date,
                                java.time.ZoneId userZone) {
        java.time.LocalDate firstQuotaDay = date.atStartOfDay(userZone)
                .withZoneSameInstant(properties.getQuotaZone()).toLocalDate();
        java.time.LocalDate lastQuotaDay = date.plusDays(1).atStartOfDay(userZone)
                .minusNanos(1).withZoneSameInstant(properties.getQuotaZone()).toLocalDate();
        for (java.time.LocalDate day = firstQuotaDay; !day.isAfter(lastQuotaDay);
                day = day.plusDays(1)) {
            String timestamp = redisTemplate.opsForValue().get(
                    EXHAUSTED_PREFIX + userId + ":" + day);
            if (timestamp != null && Instant.parse(timestamp).atZone(userZone)
                    .toLocalDate().equals(date)
                    && !Instant.now().isBefore(day.plusDays(1)
                    .atStartOfDay(properties.getQuotaZone()).toInstant())) {
                return getRemaining(userId).getRemainingScans() > 0;
            }
        }
        return false;
    }

    public Snapshot snapshot(Long userId) {
        FoodPhotoScanCreditDto credits = getRemaining(userId);
        return new Snapshot(credits.getLimit(), credits.getRemainingScans(),
                credits.getResetAt());
    }

    private FoodPhotoScanCreditDto response(Window window, int remaining,
                                             boolean consumed) {
        return FoodPhotoScanCreditDto.builder()
                .allowed(remaining > 0)
                .consumed(consumed)
                .limit(window.limit())
                .remainingScans(remaining)
                .resetAt(window.resetAt())
                .build();
    }

    private Window window(Long userId) {
        ZonedDateTime now = ZonedDateTime.now(properties.getQuotaZone());
        Instant resetAt = now.toLocalDate().plusDays(1)
                .atStartOfDay(now.getZone()).toInstant();
        long ttlSeconds = Math.max(1,
                Duration.between(now.toInstant(), resetAt).toSeconds());
        String suffix = userId + ":" + now.toLocalDate();
        return new Window(
                DAILY_PREFIX + suffix,
                COMPLETED_PREFIX + suffix,
                properties.getFreeDailyLimit(),
                resetAt,
                ttlSeconds,
                now.toLocalDate());
    }

    private void markExhausted(Long userId, java.time.LocalDate date) {
        ZonedDateTime expiresAt = date.plusDays(3)
                .atStartOfDay(properties.getQuotaZone());
        Duration ttl = Duration.between(Instant.now(), expiresAt.toInstant());
        redisTemplate.opsForValue().setIfAbsent(
                EXHAUSTED_PREFIX + userId + ":" + date,
                Instant.now().toString(),
                ttl.isNegative() || ttl.isZero() ? Duration.ofDays(1) : ttl);
    }

    private int parseUsage(String rawUsed) {
        if (rawUsed == null || rawUsed.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(rawUsed));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Invalid AI credit usage value", exception);
        }
    }

    private String normalizeToken(String idempotencyKey) {
        return idempotencyKey == null || idempotencyKey.isBlank()
                ? UUID.randomUUID().toString()
                : idempotencyKey.trim();
    }

    public record Snapshot(int limit, int remaining, Instant resetAt) {
    }

    private record Window(String usageKey, String completedKey, int limit,
                          Instant resetAt, long ttlSeconds,
                          java.time.LocalDate date) {
    }
}
