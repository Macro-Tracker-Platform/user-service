package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.SubscriptionTrialRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionTrialRedemptionRepository
        extends JpaRepository<SubscriptionTrialRedemption, Long> {
}
