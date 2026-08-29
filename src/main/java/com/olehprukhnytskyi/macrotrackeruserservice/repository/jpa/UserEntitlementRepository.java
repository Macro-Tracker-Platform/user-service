package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.UserEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserEntitlementRepository extends JpaRepository<UserEntitlement, Long> {
}
