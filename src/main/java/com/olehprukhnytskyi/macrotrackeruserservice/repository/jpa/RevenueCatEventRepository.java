package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.RevenueCatEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevenueCatEventRepository extends JpaRepository<RevenueCatEvent, String> {
}
