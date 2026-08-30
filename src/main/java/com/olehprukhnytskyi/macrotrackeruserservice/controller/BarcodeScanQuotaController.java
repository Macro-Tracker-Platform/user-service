package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.BarcodeScanQuotaDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.BarcodeScanQuotaService;
import com.olehprukhnytskyi.util.CustomHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BarcodeScanQuotaController {
    private final BarcodeScanQuotaService barcodeScanQuotaService;

    @PostMapping("/api/users/me/barcode-scans/{barcode}/reserve")
    public ResponseEntity<BarcodeScanQuotaDto> reserve(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable String barcode) {
        return ResponseEntity.ok(barcodeScanQuotaService.reserve(userId, barcode));
    }
}
