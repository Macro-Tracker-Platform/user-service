package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.PromoCodeErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCodeClaim;
import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeClaimRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceTest {
    private static final Long USER_ID = 42L;

    @Mock
    private PromoCodeRepository promoCodeRepository;
    @Mock
    private PromoCodeClaimRepository claimRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private TrialEligibilityService trialEligibilityService;

    private PromoCodeService promoCodeService;
    private PromoCode promoCode;

    @BeforeEach
    void setUp() {
        promoCodeService = new PromoCodeService(
                promoCodeRepository, claimRepository, subscriptionRepository,
                trialEligibilityService);
        promoCode = PromoCode.builder()
                .id(5L)
                .code("FRIEND20")
                .discountPercent(20)
                .monthlyOfferId("partner-20-monthly")
                .yearlyOfferId("partner-20-yearly")
                .active(true)
                .build();
    }

    @Test
    void validationNormalizesCodeAndCreatesDurableClaim() {
        PromoCodeRequestDto request = new PromoCodeRequestDto();
        request.setCode(" friend20 ");
        when(promoCodeRepository.findByCodeIgnoreCase("FRIEND20"))
                .thenReturn(Optional.of(promoCode));

        PromoCodeResponseDto response = promoCodeService.validateAndClaim(USER_ID, request);

        assertThat(response.getCode()).isEqualTo("FRIEND20");
        assertThat(response.getDiscountPercent()).isEqualTo(20);
        ArgumentCaptor<PromoCodeClaim> claim = ArgumentCaptor.forClass(PromoCodeClaim.class);
        verify(claimRepository).save(claim.capture());
        assertThat(claim.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(claim.getValue().getExpiresAt())
                .isAfter(claim.getValue().getClaimedAt());
    }

    @Test
    void unknownCodeReturnsExpectedNotFoundError() {
        PromoCodeRequestDto request = new PromoCodeRequestDto();
        request.setCode("UNKNOWN");
        when(promoCodeRepository.findByCodeIgnoreCase("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> promoCodeService.validateAndClaim(USER_ID, request))
                .isInstanceOf(NotFoundException.class)
                .satisfies(error -> assertThat(((NotFoundException) error).getErrorCode())
                        .isEqualTo(PromoCodeErrorCode.PROMO_CODE_INVALID));
    }

    @Test
    void validationTreatsValidUntilTodayAsAvailableForWholeDay() {
        promoCode.setValidUntil(Instant.now());
        PromoCodeRequestDto request = new PromoCodeRequestDto();
        request.setCode("FRIEND20");
        when(promoCodeRepository.findByCodeIgnoreCase("FRIEND20"))
                .thenReturn(Optional.of(promoCode));

        PromoCodeResponseDto response = promoCodeService.validateAndClaim(USER_ID, request);

        assertThat(response.getCode()).isEqualTo("FRIEND20");
    }

    @Test
    void validationTreatsValidFromTodayAsAvailableForWholeDay() {
        Instant laterToday = LocalDate.now(ZoneOffset.UTC)
                .plusDays(1)
                .atStartOfDay()
                .minusNanos(1)
                .toInstant(ZoneOffset.UTC);
        promoCode.setValidFrom(laterToday);
        PromoCodeRequestDto request = new PromoCodeRequestDto();
        request.setCode("FRIEND20");
        when(promoCodeRepository.findByCodeIgnoreCase("FRIEND20"))
                .thenReturn(Optional.of(promoCode));

        PromoCodeResponseDto response = promoCodeService.validateAndClaim(USER_ID, request);

        assertThat(response.getCode()).isEqualTo("FRIEND20");
    }

    @Test
    void usedTrialRemovesTrialOfferButKeepsDiscountOnlyOffer() {
        PromoCodeRequestDto request = new PromoCodeRequestDto();
        request.setCode("FRIEND20");
        when(promoCodeRepository.findByCodeIgnoreCase("FRIEND20"))
                .thenReturn(Optional.of(promoCode));
        when(trialEligibilityService.isEligible(USER_ID)).thenReturn(false);
        when(trialEligibilityService.isTrialOffer("partner-20-monthly"))
                .thenReturn(false);
        when(trialEligibilityService.isTrialOffer("partner-20-yearly"))
                .thenReturn(true);

        PromoCodeResponseDto response = promoCodeService.validateAndClaim(USER_ID, request);

        assertThat(response.getMonthlyOfferId()).isEqualTo("partner-20-monthly");
        assertThat(response.getYearlyOfferId()).isNull();
    }

    @Test
    void matchingGooglePlayOfferIsAttributedToPromoCode() {
        PromoCodeClaim claim = PromoCodeClaim.builder()
                .userId(USER_ID)
                .promoCode(promoCode)
                .claimedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        final Subscription subscription = new Subscription();
        when(claimRepository.findById(USER_ID)).thenReturn(Optional.of(claim));
        when(promoCodeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(promoCode));
        GooglePlaySubscriptionSnapshot snapshot = snapshot("partner-20-monthly");

        promoCodeService.attributePurchase(subscription, USER_ID, snapshot, true);

        assertThat(subscription.getPromoCode()).isEqualTo(promoCode);
        assertThat(subscription.getPromoCodeAppliedAt()).isNotNull();
        assertThat(claim.getConsumedAt()).isNotNull();
        assertThat(claim.getSubscription()).isEqualTo(subscription);
        verify(claimRepository).save(claim);
    }

    @Test
    void differentGooglePlayOfferDoesNotReceiveAttribution() {
        PromoCodeClaim claim = PromoCodeClaim.builder()
                .userId(USER_ID)
                .promoCode(promoCode)
                .claimedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        final Subscription subscription = new Subscription();
        when(claimRepository.findById(USER_ID)).thenReturn(Optional.of(claim));
        when(promoCodeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(promoCode));

        promoCodeService.attributePurchase(
                subscription, USER_ID, snapshot("some-other-offer"), true);

        assertThat(subscription.getPromoCode()).isNull();
        assertThat(claim.getConsumedAt()).isNull();
        verify(claimRepository, never()).save(claim);
    }

    @Test
    void retryCanAttributeExistingPurchaseCreatedAfterClaim() {
        Instant claimedAt = Instant.now().minusSeconds(60);
        PromoCodeClaim claim = PromoCodeClaim.builder()
                .userId(USER_ID)
                .promoCode(promoCode)
                .claimedAt(claimedAt)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Subscription subscription = Subscription.builder()
                .createdAt(claimedAt.plusSeconds(10))
                .build();
        when(claimRepository.findById(USER_ID)).thenReturn(Optional.of(claim));
        when(promoCodeRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(promoCode));

        promoCodeService.attributePurchase(
                subscription, USER_ID, snapshot("partner-20-monthly"), false);

        assertThat(subscription.getPromoCode()).isEqualTo(promoCode);
        assertThat(claim.getConsumedAt()).isNotNull();
    }

    @Test
    void existingPurchaseOlderThanClaimIsNotAttributedRetroactively() {
        Instant createdAt = Instant.now().minusSeconds(120);
        PromoCodeClaim claim = PromoCodeClaim.builder()
                .userId(USER_ID)
                .promoCode(promoCode)
                .claimedAt(createdAt.plusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        Subscription subscription = Subscription.builder()
                .createdAt(createdAt)
                .build();
        when(claimRepository.findById(USER_ID)).thenReturn(Optional.of(claim));

        promoCodeService.attributePurchase(
                subscription, USER_ID, snapshot("partner-20-monthly"), false);

        assertThat(subscription.getPromoCode()).isNull();
        assertThat(claim.getConsumedAt()).isNull();
    }

    private GooglePlaySubscriptionSnapshot snapshot(String offerId) {
        return new GooglePlaySubscriptionSnapshot(
                "macro_tracker_pro",
                "monthly",
                offerId,
                "SUBSCRIPTION_STATE_ACTIVE",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                true,
                true);
    }
}
