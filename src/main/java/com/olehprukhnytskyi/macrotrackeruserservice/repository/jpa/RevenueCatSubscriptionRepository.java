package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.RevenueCatSubscription;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevenueCatSubscriptionRepository
        extends JpaRepository<RevenueCatSubscription, String> {
    List<RevenueCatSubscription> findByUserId(Long userId);

    boolean existsByUserId(Long userId);
}
