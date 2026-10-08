package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.AiCreditProperties;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class AiCreditServiceTest {
    private StringRedisTemplate redisTemplate;
    private AiCreditService service;
    private ValueOperations<String, String> values;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        AiCreditProperties properties = new AiCreditProperties();
        properties.setFreeDailyLimit(3);
        properties.setQuotaZone(ZoneId.of("UTC"));
        service = new AiCreditService(redisTemplate, properties);
    }

    @Test
    void exhaustedCreditsAreRestoredAtUtcMidnight() {
        ZoneId zone = ZoneId.of("UTC");
        ZonedDateTime beforeReset = ZonedDateTime.parse("2026-10-08T23:59:59Z");
        ZonedDateTime afterReset = ZonedDateTime.parse("2026-10-09T00:00:00Z");
        when(values.get(AiCreditService.DAILY_PREFIX + "9:2026-10-08"))
                .thenReturn("3");

        try (MockedStatic<ZonedDateTime> time =
                     mockStatic(ZonedDateTime.class, CALLS_REAL_METHODS)) {
            time.when(() -> ZonedDateTime.now(zone)).thenReturn(beforeReset);
            FoodPhotoScanCreditDto exhausted = service.getRemaining(9L);
            assertThat(exhausted.getRemainingScans()).isZero();
            assertThat(exhausted.getResetAt()).isEqualTo(afterReset.toInstant());

            time.when(() -> ZonedDateTime.now(zone)).thenReturn(afterReset);
            FoodPhotoScanCreditDto restored = service.getRemaining(9L);
            assertThat(restored.getRemainingScans()).isEqualTo(3);
            assertThat(restored.isAllowed()).isTrue();
            assertThat(restored.getResetAt())
                    .isEqualTo(afterReset.plusDays(1).toInstant());
        }
        verify(values).get(AiCreditService.DAILY_PREFIX + "9:2026-10-09");
    }

    @Test
    @SuppressWarnings("unchecked")
    void successfulUseConsumesOneOfThreeDailyCredits() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                eq("scan-1"),
                eq("3"),
                any(String.class)))
                .thenReturn(1L);

        FoodPhotoScanCreditDto result = service.consume(9L, "scan-1");

        assertThat(result.isConsumed()).isTrue();
        assertThat(result.getLimit()).isEqualTo(3);
        assertThat(result.getRemainingScans()).isEqualTo(2);
        assertThat(result.getResetAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void exhaustedDailyPoolDoesNotConsumeAnotherCredit() {
        when(redisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                eq("scan-4"),
                eq("3"),
                any(String.class)))
                .thenReturn(-1L);

        FoodPhotoScanCreditDto result = service.consume(9L, "scan-4");

        assertThat(result.isConsumed()).isFalse();
        assertThat(result.getRemainingScans()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void finalCreditRetainsFirstExhaustionTimestampAcrossReset() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(),
                eq("scan-3"), eq("3"), any(String.class))).thenReturn(3L);
        service.consume(9L, "scan-3");
        verify(values).setIfAbsent(
                eq(AiCreditService.EXHAUSTED_PREFIX + 9L + ":"
                        + java.time.LocalDate.now(ZoneId.of("UTC"))),
                any(String.class), any(java.time.Duration.class));
    }

    @Test
    void exhaustionIsCheckedInUsersLocalYesterday() {
        ZoneId zone = ZoneId.of("Asia/Tokyo");
        java.time.LocalDate yesterday = java.time.LocalDate.now(zone).minusDays(1);
        java.time.Instant exhaustedAt = yesterday.atTime(0, 30).atZone(zone).toInstant();
        when(values.get(AiCreditService.EXHAUSTED_PREFIX + "9:"
                + exhaustedAt.atZone(ZoneId.of("UTC")).toLocalDate()))
                .thenReturn(exhaustedAt.toString());
        assertThat(service.wasExhausted(9L, yesterday, zone)).isTrue();
    }

    @Test
    void noHistoryMeansNoAiNotification() {
        assertThat(service.wasExhausted(9L, java.time.LocalDate.now().minusDays(1),
                ZoneId.of("UTC"))).isFalse();
    }
}
