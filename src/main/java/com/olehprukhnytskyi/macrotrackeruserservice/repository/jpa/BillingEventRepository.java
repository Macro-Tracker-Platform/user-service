package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.BillingEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEventRepository extends JpaRepository<BillingEvent, Long> {
    Optional<BillingEvent> findByGoogleMessageId(String googleMessageId);
}
