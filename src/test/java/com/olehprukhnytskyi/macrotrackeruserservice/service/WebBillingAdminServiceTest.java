package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCampaignRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebAffiliate;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebAffiliateRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class WebBillingAdminServiceTest {
    private final WebAffiliateRepository affiliates = mock(WebAffiliateRepository.class);
    private final PromoCodeRepository campaigns = mock(PromoCodeRepository.class);
    private final WebBillingAdminService service = new WebBillingAdminService(affiliates,
            campaigns, mock(WebPaymentRepository.class), mock(WebCheckoutRepository.class));

    @Test
    void legacyPartnerCodeCannotSilentlyBecomeDirectCampaign() {
        var legacy = legacyCode();
        assertThatThrownBy(() -> service.saveCampaign("FRIEND15", request(null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("LEGACY_AFFILIATE_REQUIRED");
        verify(campaigns, never()).save(any());
        assertThat(legacy.getAcquisitionType()).isEqualTo(PromoAcquisitionType.AFFILIATE);
    }

    @Test
    void explicitAffiliateMappingMigratesLegacyCode() {
        legacyCode();
        var affiliate = WebAffiliate.builder().id(42L).name("Partner").active(true).build();
        when(affiliates.findById(42L)).thenReturn(Optional.of(affiliate));
        when(campaigns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.saveCampaign(" friend15 ", request(42L));
        assertThat(result.affiliateId()).isEqualTo(42L);
        assertThat(result.affiliateMigrationRequired()).isFalse();
        assertThat(result.code()).isEqualTo("FRIEND15");
    }

    @Test
    void newDirectCampaignDoesNotRequireAffiliate() {
        when(campaigns.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var result = service.saveCampaign("DIRECT15", request(null));
        assertThat(result.affiliateId()).isNull();
        assertThat(result.affiliateMigrationRequired()).isFalse();
    }

    private PromoCode legacyCode() {
        var legacy = PromoCode.builder().id(1L).code("FRIEND15").discountPercent(15)
                .acquisitionType(PromoAcquisitionType.AFFILIATE).partnerName("Old partner")
                .build();
        when(campaigns.findByCodeIgnoreCase("FRIEND15")).thenReturn(Optional.of(legacy));
        when(campaigns.findByIdForUpdate(1L)).thenReturn(Optional.of(legacy));
        return legacy;
    }

    private WebCampaignRequestDto request(Long affiliateId) {
        return new WebCampaignRequestDto(15, affiliateId, true, null, null, null);
    }
}
