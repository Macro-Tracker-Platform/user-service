package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.FoodPhotoScanCreditDto;
import org.junit.jupiter.api.Test;

class FoodPhotoScanCreditServiceTest {
    @Test
    void delegatesToSharedAiCreditsWithFeatureScopedIdempotencyKey() {
        AiCreditService aiCreditService = mock(AiCreditService.class);
        FoodPhotoScanCreditService service = new FoodPhotoScanCreditService(aiCreditService);
        FoodPhotoScanCreditDto expected = FoodPhotoScanCreditDto.builder()
                .consumed(true)
                .limit(3)
                .remainingScans(2)
                .build();
        when(aiCreditService.consume(9L, "food-photo:scan-1")).thenReturn(expected);

        FoodPhotoScanCreditDto actual = service.consume(9L, "scan-1");

        assertThat(actual).isSameAs(expected);
        verify(aiCreditService).consume(9L, "food-photo:scan-1");
    }
}
