package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.FoodPhotoScanConsumptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class FoodPhotoScanCreditServiceTest {
    private UserRepository userRepository;
    private StringRedisTemplate redisTemplate;
    private FoodPhotoScanConsumptionRepository consumptionRepository;
    private FoodPhotoScanCreditService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userRepository = mock(UserRepository.class);
        redisTemplate = mock(StringRedisTemplate.class);
        consumptionRepository = mock(FoodPhotoScanConsumptionRepository.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        service = new FoodPhotoScanCreditService(
                userRepository, redisTemplate, consumptionRepository);
    }

    @Test
    void consumeLocksRowAndAllowsTheLastCredit() {
        User user = new User();
        user.setFoodPhotoScanCredits(1);
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));

        FoodPhotoScanCreditDto result = service.consume(9L);

        assertThat(result.isConsumed()).isTrue();
        assertThat(result.getRemainingScans()).isZero();
        assertThat(user.getFoodPhotoScanCredits()).isZero();
        verify(userRepository).save(user);
        verify(redisTemplate).delete("scans:free:credits:9");
    }

    @Test
    void consumeDoesNotGoBelowZero() {
        User user = new User();
        user.setFoodPhotoScanCredits(0);
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));

        FoodPhotoScanCreditDto result = service.consume(9L);

        assertThat(result.isConsumed()).isFalse();
        assertThat(result.getRemainingScans()).isZero();
        verify(userRepository, never()).save(any());
    }

    @Test
    void repeatedIdempotencyKeyDoesNotConsumeAnotherCredit() {
        User user = new User();
        user.setFoodPhotoScanCredits(4);
        when(userRepository.findByIdForUpdate(9L)).thenReturn(Optional.of(user));
        when(consumptionRepository.existsByUserIdAndIdempotencyKey(9L, "scan-1"))
                .thenReturn(true);

        FoodPhotoScanCreditDto result = service.consume(9L, "scan-1");

        assertThat(result.isConsumed()).isTrue();
        assertThat(result.getRemainingScans()).isEqualTo(4);
        verify(userRepository, never()).save(any());
    }
}
