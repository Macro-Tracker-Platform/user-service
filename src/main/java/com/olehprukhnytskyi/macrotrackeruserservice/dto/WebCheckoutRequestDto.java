package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.WebBillingPlan;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record WebCheckoutRequestDto(@NotNull WebBillingPlan plan, @Size(max = 64) String code) {
}
