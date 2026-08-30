package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.AcquisitionManagerPerformanceDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AcquisitionManagerRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeAttributionRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeAttributionResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.AcquisitionManager;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.AcquisitionManagerRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeClaimRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PromoAttributionAdminService {
    private static final List<SubscriptionStatus> ACTIVE_STATUSES = List.of(
            SubscriptionStatus.PRO_ACTIVE,
            SubscriptionStatus.PRO_GRACE_PERIOD,
            SubscriptionStatus.PRO_CANCELED_BUT_ACTIVE
    );

    private final AcquisitionManagerRepository managerRepository;
    private final PromoCodeRepository promoCodeRepository;
    private final PromoCodeClaimRepository claimRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Transactional
    public AcquisitionManagerPerformanceDto createManager(AcquisitionManagerRequestDto request) {
        AcquisitionManager manager = managerRepository.save(AcquisitionManager.builder()
                .name(request.getName().trim())
                .email(normalizeNullable(request.getEmail()))
                .commissionPercent(request.getCommissionPercent())
                .active(true)
                .build());
        return toPerformance(manager);
    }

    @Transactional(readOnly = true)
    public List<AcquisitionManagerPerformanceDto> getManagerPerformance() {
        return managerRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .map(this::toPerformance)
                .toList();
    }

    @Transactional
    public PromoCodeAttributionResponseDto configurePromoCode(
            String code, PromoCodeAttributionRequestDto request) {
        PromoCode promoCode = promoCodeRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Promo code was not found"));
        AcquisitionManager manager = null;
        if (request.getAcquisitionType() == PromoAcquisitionType.AFFILIATE) {
            if (request.getAcquisitionManagerId() == null
                    || request.getPartnerName() == null
                    || request.getPartnerName().isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Affiliate promo code requires a manager and partner name");
            }
            manager = managerRepository.findById(request.getAcquisitionManagerId())
                    .filter(AcquisitionManager::isActive)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Acquisition manager is missing or inactive"));
        } else if (request.getAcquisitionManagerId() != null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Direct promo code cannot have an acquisition manager");
        }
        promoCode.setAcquisitionType(request.getAcquisitionType());
        promoCode.setAcquisitionManager(manager);
        promoCode.setPartnerName(request.getAcquisitionType()
                == PromoAcquisitionType.AFFILIATE
                ? request.getPartnerName().trim() : null);
        promoCodeRepository.save(promoCode);
        return toAttribution(promoCode);
    }

    private AcquisitionManagerPerformanceDto toPerformance(AcquisitionManager manager) {
        return AcquisitionManagerPerformanceDto.builder()
                .id(manager.getId())
                .name(manager.getName())
                .email(manager.getEmail())
                .commissionPercent(manager.getCommissionPercent())
                .active(manager.isActive())
                .promoCodes(promoCodeRepository.countByAcquisitionManagerId(manager.getId()))
                .recruitedUsers(claimRepository
                        .countByPromoCodeAcquisitionManagerIdAndConsumedAtIsNotNull(
                                manager.getId()))
                .activeSubscribers(subscriptionRepository
                        .countActiveUsersByAcquisitionManagerId(
                                manager.getId(), ACTIVE_STATUSES))
                .build();
    }

    private PromoCodeAttributionResponseDto toAttribution(PromoCode promoCode) {
        AcquisitionManager manager = promoCode.getAcquisitionManager();
        return PromoCodeAttributionResponseDto.builder()
                .code(promoCode.getCode())
                .acquisitionType(promoCode.getAcquisitionType())
                .partnerName(promoCode.getPartnerName())
                .acquisitionManagerId(manager == null ? null : manager.getId())
                .acquisitionManagerName(manager == null ? null : manager.getName())
                .managerCommissionPercent(
                        manager == null ? null : manager.getCommissionPercent())
                .build();
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
