package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.AcquisitionManagerPerformanceDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeAttributionRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeAttributionResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.AcquisitionManager;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.AcquisitionManagerRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeClaimRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class PromoAttributionAdminServiceTest {
    @Mock
    private AcquisitionManagerRepository managerRepository;
    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PromoCodeClaimRepository claimRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;

    private PromoAttributionAdminService service;

    @BeforeEach
    void setUp() {
        service = new PromoAttributionAdminService(
                managerRepository, promoCodeRepository, claimRepository,
                subscriptionRepository);
    }

    @Test
    void affiliatePromoCodeIsLinkedToBloggerAndManager() {
        final AcquisitionManager manager = AcquisitionManager.builder()
                .id(3L)
                .name("Growth manager")
                .commissionPercent(new BigDecimal("12.50"))
                .active(true)
                .build();
        final PromoCode promoCode = PromoCode.builder()
                .code("COACH20")
                .active(true)
                .build();
        PromoCodeAttributionRequestDto request = new PromoCodeAttributionRequestDto();
        request.setAcquisitionType(PromoAcquisitionType.AFFILIATE);
        request.setAcquisitionManagerId(3L);
        request.setPartnerName("Coach Anna");
        when(promoCodeRepository.findByCodeIgnoreCase("COACH20"))
                .thenReturn(Optional.of(promoCode));
        when(managerRepository.findById(3L)).thenReturn(Optional.of(manager));

        PromoCodeAttributionResponseDto response = service.configurePromoCode("COACH20", request);

        assertThat(promoCode.getAcquisitionType())
                .isEqualTo(PromoAcquisitionType.AFFILIATE);
        assertThat(promoCode.getPartnerName()).isEqualTo("Coach Anna");
        assertThat(promoCode.getAcquisitionManager()).isEqualTo(manager);
        assertThat(response.getManagerCommissionPercent())
                .isEqualByComparingTo("12.50");
    }

    @Test
    void directPromoCodeCannotBeLinkedToManager() {
        PromoCode promoCode = PromoCode.builder().code("SOCIAL10").active(true).build();
        PromoCodeAttributionRequestDto request = new PromoCodeAttributionRequestDto();
        request.setAcquisitionType(PromoAcquisitionType.DIRECT);
        request.setAcquisitionManagerId(3L);
        when(promoCodeRepository.findByCodeIgnoreCase("SOCIAL10"))
                .thenReturn(Optional.of(promoCode));

        assertThatThrownBy(() -> service.configurePromoCode("SOCIAL10", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Direct promo code");
    }

    @Test
    void performanceCountsDistinctRecruitedAndActiveUsers() {
        AcquisitionManager manager = AcquisitionManager.builder()
                .id(3L)
                .name("Growth manager")
                .commissionPercent(new BigDecimal("12.50"))
                .active(true)
                .build();
        when(managerRepository.findAll(any(Sort.class))).thenReturn(List.of(manager));
        when(promoCodeRepository.countByAcquisitionManagerId(3L)).thenReturn(4L);
        when(claimRepository
                .countByPromoCodeAcquisitionManagerIdAndConsumedAtIsNotNull(3L))
                .thenReturn(25L);
        when(subscriptionRepository.countActiveUsersByAcquisitionManagerId(
                any(), any())).thenReturn(9L);

        List<AcquisitionManagerPerformanceDto> response = service.getManagerPerformance();

        assertThat(response).singleElement().satisfies(performance -> {
            assertThat(performance.getPromoCodes()).isEqualTo(4);
            assertThat(performance.getRecruitedUsers()).isEqualTo(25);
            assertThat(performance.getActiveSubscribers()).isEqualTo(9);
        });
    }
}
