package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.AiCreditProperties;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.MockedStatic;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

// Run against a dedicated local Redis with -Dai.quota.redis.port=<port>.
@EnabledIfSystemProperty(named = "ai.quota.redis.port", matches = "\\d+")
class AiCreditRedisTest {
    private LettuceConnectionFactory connection;
    private StringRedisTemplate redis;
    private AiCreditService service;
    private long userId;

    @BeforeEach
    void setUp() {
        connection = new LettuceConnectionFactory("127.0.0.1",
                Integer.getInteger("ai.quota.redis.port"));
        connection.afterPropertiesSet();
        redis = new StringRedisTemplate(connection);
        userId = -ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        service = new AiCreditService(redis, new AiCreditProperties());
    }

    @AfterEach
    void tearDown() {
        var keys = redis.keys("*:" + userId + "*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
        connection.destroy();
    }

    @Test
    void retryDoesNotConsumeAgainAndCreditsResetWithOldKeyStillPresent() {
        ZoneId zone = ZoneId.of("UTC");
        ZonedDateTime day = ZonedDateTime.of(2026, 10, 8, 12, 0, 0, 0, zone);
        ZonedDateTime nextDay = day.plusDays(1);
        try (MockedStatic<ZonedDateTime> time =
                     mockStatic(ZonedDateTime.class, CALLS_REAL_METHODS)) {
            time.when(() -> ZonedDateTime.now(zone)).thenReturn(day);
            assertThat(service.consume(userId, "photo:first").getRemainingScans()).isEqualTo(2);
            assertThat(service.consume(userId, "photo:first").getRemainingScans()).isEqualTo(2);
            service.consume(userId, "voice:second");
            service.consume(userId, "nutrition-label:third");
            assertThat(service.consume(userId, "fourth").isConsumed()).isFalse();
            assertThat(service.getRemaining(userId).getRemainingScans()).isZero();

            time.when(() -> ZonedDateTime.now(zone)).thenReturn(nextDay);
            assertThat(service.getRemaining(userId).getRemainingScans()).isEqualTo(3);
            assertThat(redis.opsForValue().get(
                    AiCreditService.DAILY_PREFIX + userId + ":2026-10-08")).isEqualTo("3");
            assertThat(service.consume(userId, "new-day").getRemainingScans()).isEqualTo(2);
        }
    }

    @Test
    void concurrentConsumptionCannotSpendMoreThanThreeCredits() throws Exception {
        try (var executor = Executors.newFixedThreadPool(12)) {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<FoodPhotoScanCreditDto>> results = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                String token = "parallel-" + i;
                results.add(executor.submit(() -> {
                    assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                    return service.consume(userId, token);
                }));
            }
            start.countDown();
            int consumed = 0;
            for (Future<FoodPhotoScanCreditDto> result : results) {
                if (result.get(10, TimeUnit.SECONDS).isConsumed()) {
                    consumed++;
                }
            }
            assertThat(consumed).isEqualTo(3);
            assertThat(service.getRemaining(userId).getRemainingScans()).isZero();
        }
    }
}
