package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.BarcodeScanQuotaDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.BarcodeScanProperties;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class BarcodeScanQuotaServiceTest {
    private static final long USER_ID = 42L;

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private SubscriptionService subscriptionService;

    private BarcodeScanQuotaService quotaService;

    @BeforeEach
    void setUp() {
        BarcodeScanProperties properties = new BarcodeScanProperties();
        properties.setFreeDailyUniqueLimit(5);
        properties.setQuotaZone(ZoneId.of("UTC"));
        quotaService = new BarcodeScanQuotaService(
                redisTemplate, subscriptionService, properties);
    }

    @Test
    void freeUserCanReserveUntilBackendReportsTheLimit() {
        when(subscriptionService.getEntitlement(USER_ID)).thenReturn(freeEntitlement());
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(5L, -1L);

        BarcodeScanQuotaDto fifth = quotaService.reserve(USER_ID, "barcode-5");
        BarcodeScanQuotaDto sixth = quotaService.reserve(USER_ID, "barcode-6");

        assertThat(fifth.isAllowed()).isTrue();
        assertThat(fifth.getRemaining()).isZero();
        assertThat(sixth.isAllowed()).isFalse();
        assertThat(sixth.getLimit()).isEqualTo(5);
        assertThat(sixth.getRemaining()).isZero();
        assertThat(sixth.getResetAt()).isNotNull();
    }

    @Test
    void repeatedBarcodeUsesAtomicRedisResultWithoutConsumingAnotherSlot() {
        when(subscriptionService.getEntitlement(USER_ID)).thenReturn(freeEntitlement());
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(3L, 3L);

        BarcodeScanQuotaDto first = quotaService.reserve(USER_ID, " barcode-3 ");
        BarcodeScanQuotaDto repeated = quotaService.reserve(USER_ID, "barcode-3");

        assertThat(first.getRemaining()).isEqualTo(2);
        assertThat(repeated.isAllowed()).isTrue();
        assertThat(repeated.getRemaining()).isEqualTo(2);
    }

    @Test
    void premiumUserBypassesRedisWithUnlimitedAllowance() {
        when(subscriptionService.getEntitlement(USER_ID)).thenReturn(proEntitlement());

        BarcodeScanQuotaDto quota = quotaService.reserve(USER_ID, "barcode-1");

        assertThat(quota.isAllowed()).isTrue();
        assertThat(quota.isUnlimited()).isTrue();
        assertThat(quota.getLimit()).isNull();
        verify(redisTemplate, never()).execute(
                any(RedisScript.class), anyList(), anyString(), anyString(), anyString());
    }

    @Test
    void blankBarcodeIsRejectedBeforeEntitlementLookup() {
        assertThatThrownBy(() -> quotaService.reserve(USER_ID, "  "))
                .isInstanceOf(IllegalArgumentException.class);
        verify(subscriptionService, never()).getEntitlement(USER_ID);
    }

    private EntitlementResponseDto freeEntitlement() {
        return EntitlementResponseDto.builder().plan("FREE").build();
    }

    private EntitlementResponseDto proEntitlement() {
        return EntitlementResponseDto.builder().plan("PRO").build();
    }
}
