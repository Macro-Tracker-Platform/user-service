package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.model.Subscription;
import com.olehprukhnytskyi.macrotrackeruserservice.model.SubscriptionTrialRedemption;
import com.olehprukhnytskyi.macrotrackeruserservice.properties.GooglePlayProperties;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.SubscriptionTrialRedemptionRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class TrialEligibilityService {
    private final SubscriptionTrialRedemptionRepository redemptionRepository;
    private final GooglePlayProperties properties;

    @Transactional(readOnly = true)
    public boolean isEligible(Long userId) {
        return !redemptionRepository.existsById(userId);
    }

    public boolean isTrialOffer(String offerId) {
        return offerId != null && properties.getTrialOfferIds().contains(offerId);
    }

    @Transactional
    public void recordRedemption(
            Long userId,
            Subscription subscription,
            String offerId,
            boolean newPurchase
    ) {
        if (!newPurchase || !isTrialOffer(offerId)) {
            return;
        }
        SubscriptionTrialRedemption existing = redemptionRepository.findById(userId)
                .orElse(null);
        if (existing != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Free trial has already been used by this account");
        }
        redemptionRepository.save(SubscriptionTrialRedemption.builder()
                .userId(userId)
                .subscription(subscription)
                .offerId(offerId)
                .redeemedAt(Instant.now())
                .build());
    }
}
