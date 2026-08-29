package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.InsertAffiliateWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.InsertAffiliateProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InsertAffiliateWebhookServiceTest {
    @Mock
    private PromoCodeRepository promoCodeRepository;

    private InsertAffiliateWebhookService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        InsertAffiliateProperties properties = new InsertAffiliateProperties();
        properties.setWebhookAuthorization("insert-secret");
        properties.setDefaultCommissionPercent(new BigDecimal("25.00"));
        properties.setDefaultDiscountPercent(20);
        properties.setMonthlyOfferId("partner-20-monthly");
        properties.setYearlyOfferId("partner-20-yearly");
        service = new InsertAffiliateWebhookService(properties, promoCodeRepository);
    }

    @Test
    void rejectsWrongAuthorization() {
        assertThatThrownBy(() -> service.verifyAuthorization("Bearer wrong"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error)
                        .getStatusCode().value()).isEqualTo(401));
    }

    @Test
    void createsPromoCodeFromAffiliatePayloadUsingConfiguredOffers() throws Exception {
        InsertAffiliateWebhookDto payload = objectMapper.readValue("""
                {
                  "event": "affiliate.created",
                  "affiliate_id": "ia_45fee001",
                  "email": "blogger@example.com",
                  "deep_link": "https://myapp.link/ref/abc123",
                  "occurred_at": "2026-03-18T12:00:00.000Z"
                }
                """, InsertAffiliateWebhookDto.class);
        when(promoCodeRepository.findByInsertAffiliateId("ia_45fee001"))
                .thenReturn(Optional.empty());
        when(promoCodeRepository.findByCodeIgnoreCase("ABC123"))
                .thenReturn(Optional.empty());

        service.process(payload);

        ArgumentCaptor<PromoCode> promoCode = ArgumentCaptor.forClass(PromoCode.class);
        verify(promoCodeRepository).save(promoCode.capture());
        assertThat(promoCode.getValue().getCode()).isEqualTo("ABC123");
        assertThat(promoCode.getValue().getInsertAffiliateId()).isEqualTo("ia_45fee001");
        assertThat(promoCode.getValue().getPartnerName()).isEqualTo("blogger@example.com");
        assertThat(promoCode.getValue().getDiscountPercent()).isEqualTo(20);
        assertThat(promoCode.getValue().getMonthlyOfferId()).isEqualTo("partner-20-monthly");
        assertThat(promoCode.getValue().getYearlyOfferId()).isEqualTo("partner-20-yearly");
        assertThat(promoCode.getValue().isActive()).isTrue();
    }

    @Test
    void deactivatesExistingPromoCode() throws Exception {
        PromoCode existing = PromoCode.builder()
                .id(10L)
                .code("ABC123")
                .insertAffiliateId("ia_45fee001")
                .insertAffiliateShortCode("ABC123")
                .partnerName("blogger@example.com")
                .commissionPercent(BigDecimal.TEN)
                .discountPercent(20)
                .monthlyOfferId("partner-20-monthly")
                .yearlyOfferId("partner-20-yearly")
                .active(true)
                .build();
        InsertAffiliateWebhookDto payload = objectMapper.readValue("""
                {
                  "event": "affiliate.deactivated",
                  "affiliate_id": "ia_45fee001",
                  "email": "blogger@example.com",
                  "deep_link": "https://myapp.link/ref/abc123",
                  "occurred_at": "2026-03-18T14:30:00.000Z"
                }
                """, InsertAffiliateWebhookDto.class);
        when(promoCodeRepository.findByInsertAffiliateId("ia_45fee001"))
                .thenReturn(Optional.of(existing));

        service.process(payload);

        assertThat(existing.isActive()).isFalse();
        verify(promoCodeRepository).save(existing);
    }
}
