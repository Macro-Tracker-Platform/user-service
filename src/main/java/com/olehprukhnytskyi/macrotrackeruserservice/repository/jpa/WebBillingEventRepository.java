package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebBillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebBillingEventRepository extends JpaRepository<WebBillingEvent, String> {
}
