package com.olehprukhnytskyi.macrotrackeruserservice.service;

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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PromoCodeService {
    static final Duration CLAIM_DURATION = Duration.ofHours(24);
    private static final String MONTHLY_BASE_PLAN_ID = "monthly";
    private static final String YEARLY_BASE_PLAN_ID = "yearly";

    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeClaimRepository claimRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public PromoCodeResponseDto validateAndClaim(Long userId, PromoCodeRequestDto request) {
        String normalizedCode = normalize(request.getCode());
        PromoCode promoCode = promoCodeRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(this::invalidCode);
        requireAvailable(promoCode, Instant.now());
        if (isBlank(promoCode.getMonthlyOfferId())
                && isBlank(promoCode.getYearlyOfferId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Promo code has no Google Play offers configured");
        }
        Instant now = Instant.now();
        claimRepository.save(PromoCodeClaim.builder()
                .userId(userId)
                .promoCode(promoCode)
                .claimedAt(now)
                .expiresAt(now.plus(CLAIM_DURATION))
                .build());
        return PromoCodeResponseDto.builder()
                .code(promoCode.getCode())
                .discountPercent(promoCode.getDiscountPercent())
                .monthlyOfferId(promoCode.getMonthlyOfferId())
                .yearlyOfferId(promoCode.getYearlyOfferId())
                .build();
    }

    @Transactional
    public void attributeNewPurchase(
            Subscription subscription,
            Long userId,
            GooglePlaySubscriptionSnapshot snapshot
    ) {
        PromoCodeClaim claim = claimRepository.findById(userId).orElse(null);
        if (claim == null) {
            return;
        }
        claimRepository.delete(claim);
        Instant now = Instant.now();
        if (claim.getExpiresAt().isBefore(now)) {
            return;
        }
        PromoCode promoCode = promoCodeRepository
                .findByIdForUpdate(claim.getPromoCode().getId()).orElse(null);
        if (promoCode == null || !isAvailable(promoCode, now)) {
            return;
        }
        String expectedOfferId = offerIdForBasePlan(promoCode, snapshot.basePlanId());
        if (isBlank(expectedOfferId) || !expectedOfferId.equals(snapshot.offerId())) {
            return;
        }
        subscription.setPromoCode(promoCode);
        subscription.setPromoCodeAppliedAt(now);
    }

    private String offerIdForBasePlan(PromoCode promoCode, String basePlanId) {
        if (MONTHLY_BASE_PLAN_ID.equalsIgnoreCase(basePlanId)) {
            return promoCode.getMonthlyOfferId();
        }
        if (YEARLY_BASE_PLAN_ID.equalsIgnoreCase(basePlanId)) {
            return promoCode.getYearlyOfferId();
        }
        return null;
    }

    private void requireAvailable(PromoCode promoCode, Instant now) {
        if (!isAvailable(promoCode, now)) {
            throw invalidCode();
        }
    }

    private boolean isAvailable(PromoCode promoCode, Instant now) {
        if (!promoCode.isActive()
                || isAfterToday(promoCode.getValidFrom(), now)
                || isBeforeToday(promoCode.getValidUntil(), now)) {
            return false;
        }
        if (promoCode.getMaxRedemptions() == null) {
            return true;
        }
        return subscriptionRepository.countDistinctUsersByPromoCodeId(promoCode.getId())
                < promoCode.getMaxRedemptions();
    }

    private boolean isAfterToday(Instant value, Instant now) {
        return value != null && toUtcDate(value).isAfter(toUtcDate(now));
    }

    private boolean isBeforeToday(Instant value, Instant now) {
        return value != null && toUtcDate(value).isBefore(toUtcDate(now));
    }

    private LocalDate toUtcDate(Instant value) {
        return value.atZone(ZoneOffset.UTC).toLocalDate();
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private NotFoundException invalidCode() {
        return new NotFoundException(
                PromoCodeErrorCode.PROMO_CODE_INVALID,
                "Promo code is invalid or no longer available");
    }
}
