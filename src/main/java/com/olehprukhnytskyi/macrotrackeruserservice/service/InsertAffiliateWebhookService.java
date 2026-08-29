package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.InsertAffiliateWebhookDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.PromoCode;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.InsertAffiliateProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.PromoCodeRepository;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class InsertAffiliateWebhookService {
    private final InsertAffiliateProperties properties;
    private final PromoCodeRepository promoCodeRepository;

    public void verifyAuthorization(String authorization) {
        String expected = properties.getWebhookAuthorization();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Insert Affiliate webhook authorization is not configured");
        }
        if (!authorizationMatches(expected, authorization)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid Insert Affiliate webhook authorization");
        }
    }

    @Transactional
    public void process(InsertAffiliateWebhookDto payload) {
        if (payload == null) {
            throw invalidPayload();
        }
        String code = normalize(coalesce(
                nestedPromoCode(payload),
                nestedAffiliateShortCode(payload),
                payload.getShortCode(),
                payload.getCode(),
                shortCodeFromDeepLink(payload.getDeepLink()),
                nestedAffiliateDeepLinkCode(payload)));
        if (code.isBlank()) {
            throw invalidPayload();
        }

        String affiliateId = trim(coalesce(
                nestedAffiliateId(payload),
                payload.getAffiliateId()));

        PromoCode promoCode = findExistingPromoCode(affiliateId, code);
        promoCode.setCode(code);
        promoCode.setInsertAffiliateId(blankToNull(affiliateId));
        promoCode.setInsertAffiliateShortCode(code);
        promoCode.setPartnerName(coalesce(
                nestedAffiliateName(payload),
                payload.getName(),
                payload.getEmail(),
                affiliateId,
                code));
        promoCode.setCommissionPercent(coalesceBigDecimal(
                nestedAffiliateCommission(payload),
                payload.getCommissionPercent(),
                properties.getDefaultCommissionPercent(),
                BigDecimal.ZERO));
        promoCode.setDiscountPercent(coalesceInteger(
                nestedPromoDiscount(payload),
                nestedAffiliateDiscount(payload),
                payload.getDiscountPercent(),
                properties.getDefaultDiscountPercent(),
                0));
        promoCode.setMonthlyOfferId(blankToNull(coalesce(
                nestedPromoMonthlyOfferId(payload),
                nestedAffiliateMonthlyOfferId(payload),
                payload.getMonthlyOfferId(),
                properties.getMonthlyOfferId())));
        promoCode.setYearlyOfferId(blankToNull(coalesce(
                nestedPromoYearlyOfferId(payload),
                nestedAffiliateYearlyOfferId(payload),
                payload.getYearlyOfferId(),
                properties.getYearlyOfferId())));
        promoCode.setActive(resolveActive(payload));
        promoCodeRepository.save(promoCode);
    }

    private PromoCode findExistingPromoCode(String affiliateId, String code) {
        if (affiliateId != null && !affiliateId.isBlank()) {
            return promoCodeRepository.findByInsertAffiliateId(affiliateId)
                    .orElseGet(() -> promoCodeRepository.findByCodeIgnoreCase(code)
                            .orElseGet(PromoCode::new));
        }
        return promoCodeRepository.findByCodeIgnoreCase(code).orElseGet(PromoCode::new);
    }

    private boolean resolveActive(InsertAffiliateWebhookDto payload) {
        Boolean active = coalesceBoolean(
                nestedPromoActive(payload),
                nestedAffiliateActive(payload),
                payload.getActive());
        if (active != null) {
            return active;
        }
        String status = lower(coalesce(
                nestedPromoStatus(payload),
                nestedAffiliateStatus(payload),
                payload.getStatus()));
        if (status != null && (status.contains("inactive")
                || status.contains("disabled")
                || status.contains("deleted")
                || status.contains("archived"))) {
            return false;
        }
        String event = lower(coalesce(payload.getEventType(), payload.getAction()));
        return event == null
                || !(event.contains("deleted")
                || event.contains("disabled")
                || event.contains("deactivated")
                || event.contains("archived"));
    }

    private String shortCodeFromDeepLink(String deepLink) {
        if (deepLink == null || deepLink.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(deepLink);
            String query = uri.getQuery();
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] keyValue = part.split("=", 2);
                    if (keyValue.length == 2
                            && ("code".equalsIgnoreCase(keyValue[0])
                            || "short_code".equalsIgnoreCase(keyValue[0])
                            || "ref".equalsIgnoreCase(keyValue[0]))) {
                        return keyValue[1];
                    }
                }
            }
            String path = uri.getPath();
            if (path == null || path.isBlank()) {
                return null;
            }
            String[] segments = path.split("/");
            return segments.length == 0 ? null : segments[segments.length - 1];
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalize(String code) {
        String trimmed = trim(code);
        return trimmed == null ? "" : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isBlank() ? null : trimmed;
    }

    private String lower(String value) {
        String trimmed = trim(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String coalesce(String... values) {
        for (String value : values) {
            String trimmed = trim(value);
            if (trimmed != null && !trimmed.isBlank()) {
                return trimmed;
            }
        }
        return null;
    }

    private BigDecimal coalesceBigDecimal(BigDecimal... values) {
        for (BigDecimal value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Integer coalesceInteger(Integer... values) {
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private Boolean coalesceBoolean(Boolean... values) {
        for (Boolean value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private ResponseStatusException invalidPayload() {
        return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Insert Affiliate webhook payload must include an affiliate promo code");
    }

    private boolean authorizationMatches(String expected, String actual) {
        String expectedTrimmed = trim(expected);
        String actualTrimmed = trim(actual);
        if (expectedTrimmed == null || actualTrimmed == null) {
            return false;
        }
        if (expectedTrimmed.equals(actualTrimmed)) {
            return true;
        }
        if (expectedTrimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
            return false;
        }
        return ("Bearer " + expectedTrimmed).equals(actualTrimmed);
    }

    private String nestedAffiliateId(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null ? null : payload.getAffiliate().getAffiliateId();
    }

    private String nestedAffiliateShortCode(InsertAffiliateWebhookDto payload) {
        if (payload.getAffiliate() == null) {
            return null;
        }
        return coalesce(payload.getAffiliate().getShortCode(), payload.getAffiliate().getCode());
    }

    private String nestedPromoCode(InsertAffiliateWebhookDto payload) {
        if (payload.getPromoCode() == null) {
            return null;
        }
        return coalesce(payload.getPromoCode().getShortCode(), payload.getPromoCode().getCode());
    }

    private String nestedAffiliateDeepLinkCode(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null
                ? null
                : shortCodeFromDeepLink(payload.getAffiliate().getDeepLink());
    }

    private String nestedAffiliateName(InsertAffiliateWebhookDto payload) {
        if (payload.getAffiliate() == null) {
            return null;
        }
        return coalesce(payload.getAffiliate().getName(), payload.getAffiliate().getEmail());
    }

    private BigDecimal nestedAffiliateCommission(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null
                ? null : payload.getAffiliate().getCommissionPercent();
    }

    private Integer nestedAffiliateDiscount(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null ? null : payload.getAffiliate().getDiscountPercent();
    }

    private Integer nestedPromoDiscount(InsertAffiliateWebhookDto payload) {
        return payload.getPromoCode() == null ? null : payload.getPromoCode().getDiscountPercent();
    }

    private String nestedAffiliateMonthlyOfferId(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null ? null : payload.getAffiliate().getMonthlyOfferId();
    }

    private String nestedAffiliateYearlyOfferId(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null ? null : payload.getAffiliate().getYearlyOfferId();
    }

    private String nestedPromoMonthlyOfferId(InsertAffiliateWebhookDto payload) {
        return payload.getPromoCode() == null ? null : payload.getPromoCode().getMonthlyOfferId();
    }

    private String nestedPromoYearlyOfferId(InsertAffiliateWebhookDto payload) {
        return payload.getPromoCode() == null ? null : payload.getPromoCode().getYearlyOfferId();
    }

    private Boolean nestedAffiliateActive(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null ? null : payload.getAffiliate().getActive();
    }

    private Boolean nestedPromoActive(InsertAffiliateWebhookDto payload) {
        return payload.getPromoCode() == null ? null : payload.getPromoCode().getActive();
    }

    private String nestedAffiliateStatus(InsertAffiliateWebhookDto payload) {
        return payload.getAffiliate() == null ? null : payload.getAffiliate().getStatus();
    }

    private String nestedPromoStatus(InsertAffiliateWebhookDto payload) {
        return payload.getPromoCode() == null ? null : payload.getPromoCode().getStatus();
    }
}
