package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.BarcodeScanQuotaDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.BarcodeScanProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BarcodeScanQuotaService {
    static final String DAILY_UNIQUE_PREFIX = "barcode-scan:unique:daily:";
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            new DefaultRedisScript<>("""
                    local used = redis.call('SCARD', KEYS[1])
                    local ttl = redis.call('TTL', KEYS[1])
                    if used > 0 and ttl < 1 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
                    if redis.call('SISMEMBER', KEYS[1], ARGV[1]) == 1 then return used end
                    if used >= tonumber(ARGV[2]) then return -1 end
                    redis.call('SADD', KEYS[1], ARGV[1])
                    if used == 0 then redis.call('EXPIRE', KEYS[1], ARGV[3]) end
                    return used + 1
                    """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final SubscriptionService subscriptionService;
    private final BarcodeScanProperties properties;

    public BarcodeScanQuotaDto reserve(Long userId, String barcode) {
        String normalizedBarcode = normalize(barcode);
        EntitlementResponseDto entitlement = subscriptionService.getEntitlement(userId);
        if (entitlement != null && "PRO".equalsIgnoreCase(entitlement.getPlan())) {
            return BarcodeScanQuotaDto.builder()
                    .allowed(true)
                    .unlimited(true)
                    .build();
        }

        ZonedDateTime now = ZonedDateTime.now(properties.getQuotaZone());
        Instant resetAt = now.toLocalDate().plusDays(1)
                .atStartOfDay(now.getZone()).toInstant();
        long ttlSeconds = Math.max(1, Duration.between(now.toInstant(), resetAt).toSeconds());
        int limit = properties.getFreeDailyUniqueLimit();
        String key = DAILY_UNIQUE_PREFIX + userId + ":" + now.toLocalDate();
        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(key),
                normalizedBarcode,
                String.valueOf(limit),
                String.valueOf(ttlSeconds));
        if (result == null) {
            throw new IllegalStateException("Could not reserve barcode scan quota");
        }
        boolean allowed = result >= 0;
        int used = allowed ? result.intValue() : limit;
        return BarcodeScanQuotaDto.builder()
                .allowed(allowed)
                .unlimited(false)
                .limit(limit)
                .remaining(Math.max(0, limit - used))
                .resetAt(resetAt)
                .build();
    }

    private String normalize(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            throw new IllegalArgumentException("Barcode must not be blank");
        }
        return barcode.trim();
    }
}
