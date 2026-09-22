package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.AiCreditProperties;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

class AiCreditServiceTest {
    private StringRedisTemplate redisTemplate;
    private AiCreditService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        AiCreditProperties properties = new AiCreditProperties();
        properties.setFreeDailyLimit(3);
        properties.setQuotaZone(ZoneId.of("UTC"));
        service = new AiCreditService(redisTemplate, properties);
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
}
