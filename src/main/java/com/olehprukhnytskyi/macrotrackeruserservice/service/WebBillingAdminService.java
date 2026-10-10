package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebAffiliateRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebAffiliateResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCampaignRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCampaignResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCommissionResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.model.WebAffiliate;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebAffiliateRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebPaymentRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class WebBillingAdminService {
    private final WebAffiliateRepository affiliates;
    private final PromoCodeRepository campaigns;
    private final WebPaymentRepository payments;
    private final WebCheckoutRepository checkouts;

    @Transactional
    public WebAffiliateResponseDto createAffiliate(WebAffiliateRequestDto request) {
        WebAffiliate referrer = request.referrerId() == null ? null
                : affiliates.findById(request.referrerId()).orElseThrow(this::notFound);
        if (referrer != null && !referrer.isActive()) {
            throw notFound();
        }
        return affiliate(affiliates.save(WebAffiliate.builder().name(request.name().trim())
                .email(request.email()).referrer(referrer).active(true).build()));
    }

    @Transactional(readOnly = true)
    public List<WebAffiliateResponseDto> affiliates() {
        return affiliates.findAll(Sort.by("id")).stream().map(this::affiliate).toList();
    }

    @Transactional
    public WebCampaignResponseDto saveCampaign(String code, WebCampaignRequestDto request) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9_-]{3,64}")
                || (request.validFrom() != null && request.validUntil() != null
                    && request.validFrom().isAfter(request.validUntil()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_WEB_CAMPAIGN");
        }
        WebAffiliate affiliate = request.affiliateId() == null ? null
                : affiliates.findById(request.affiliateId()).orElseThrow(this::notFound);
        PromoCode promo = campaigns.findByCodeIgnoreCase(normalized)
                .map(found -> campaigns.findByIdForUpdate(found.getId()).orElseThrow())
                .orElseGet(() -> PromoCode.builder().code(normalized).build());
        if (affiliate == null && needsAffiliateMigration(promo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "LEGACY_AFFILIATE_REQUIRED");
        }
        promo.setWebAffiliate(affiliate);
        promo.setAcquisitionType(affiliate == null
                ? PromoAcquisitionType.DIRECT : PromoAcquisitionType.AFFILIATE);
        promo.setPartnerName(affiliate == null ? null : affiliate.getName());
        promo.setDiscountPercent(request.discountPercent());
        promo.setActive(request.active());
        promo.setValidFrom(request.validFrom());
        promo.setValidUntil(request.validUntil());
        promo.setMaxRedemptions(request.maxRedemptions());
        return campaign(campaigns.save(promo));
    }

    @Transactional(readOnly = true)
    public List<WebCampaignResponseDto> campaigns() {
        return campaigns.findAll(Sort.by("code")).stream().map(this::campaign).toList();
    }

    @Transactional(readOnly = true)
    public Page<WebCommissionResponseDto> commissions(int page, int size) {
        return payments.findAll(PageRequest.of(Math.max(0, page), Math.max(1, Math.min(200, size)),
                Sort.by(Sort.Direction.DESC, "paidAt").and(Sort.by("id"))))
                .map(payment -> {
                    var checkout = checkouts.findById(payment.getCheckoutId()).orElseThrow();
                    return new WebCommissionResponseDto(payment.getId(), checkout.getId(),
                            checkout.getPlan().name(), payment.getCurrency(),
                            payment.getAmountPaid(),
                            payment.getRefundedAmount(), checkout.getAffiliateId(),
                            checkout.getPartnerName(), payment.getCommissionAmount(),
                            checkout.getReferrerId(), checkout.getReferrerName(),
                            payment.getReferrerCommissionAmount(), payment.getPaidAt());
                });
    }

    private WebCampaignResponseDto campaign(PromoCode promo) {
        return new WebCampaignResponseDto(promo.getCode(), promo.getDiscountPercent(),
                promo.getWebAffiliate() == null ? null : promo.getWebAffiliate().getId(),
                promo.isActive(), promo.getValidFrom(), promo.getValidUntil(),
                promo.getMaxRedemptions(), needsAffiliateMigration(promo));
    }

    private boolean needsAffiliateMigration(PromoCode promo) {
        return promo.getAcquisitionType() == PromoAcquisitionType.AFFILIATE
                && promo.getWebAffiliate() == null;
    }

    private WebAffiliateResponseDto affiliate(WebAffiliate affiliate) {
        return new WebAffiliateResponseDto(affiliate.getId(), affiliate.getName(),
                affiliate.getEmail(), affiliate.getReferrer() == null
                    ? null : affiliate.getReferrer().getId(), affiliate.isActive());
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "AFFILIATE_NOT_FOUND");
    }
}
