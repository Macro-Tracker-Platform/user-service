package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.annotation.RequireRole;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AcquisitionManagerPerformanceDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AcquisitionManagerRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeAttributionRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.PromoCodeAttributionResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.PromoAttributionAdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions/admin/promo-attribution")
public class PromoAttributionAdminController {
    private final PromoAttributionAdminService service;

    @RequireRole("ADMIN")
    @PostMapping("/managers")
    public ResponseEntity<AcquisitionManagerPerformanceDto> createManager(
            @RequestBody @Valid AcquisitionManagerRequestDto request) {
        return ResponseEntity.ok(service.createManager(request));
    }

    @RequireRole("ADMIN")
    @GetMapping("/managers")
    public ResponseEntity<List<AcquisitionManagerPerformanceDto>> managerPerformance() {
        return ResponseEntity.ok(service.getManagerPerformance());
    }

    @RequireRole("ADMIN")
    @PutMapping("/promo-codes/{code}")
    public ResponseEntity<PromoCodeAttributionResponseDto> configurePromoCode(
            @PathVariable String code,
            @RequestBody @Valid PromoCodeAttributionRequestDto request) {
        return ResponseEntity.ok(service.configurePromoCode(code, request));
    }
}
