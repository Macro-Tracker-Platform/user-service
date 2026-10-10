package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.annotation.RequireRole;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebAffiliateRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebAffiliateResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCampaignRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCampaignResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.WebCommissionResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.WebBillingAdminService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/subscriptions/admin/web-billing")
public class WebBillingAdminController {
    private final WebBillingAdminService service;

    @RequireRole("ADMIN")
    @PostMapping("/affiliates")
    public WebAffiliateResponseDto createAffiliate(
            @RequestBody @Valid WebAffiliateRequestDto request) {
        return service.createAffiliate(request);
    }

    @RequireRole("ADMIN")
    @GetMapping("/affiliates")
    public List<WebAffiliateResponseDto> affiliates() {
        return service.affiliates();
    }

    @RequireRole("ADMIN")
    @PutMapping("/campaigns/{code}")
    public WebCampaignResponseDto campaign(@PathVariable String code,
                                          @RequestBody @Valid WebCampaignRequestDto request) {
        return service.saveCampaign(code, request);
    }

    @RequireRole("ADMIN")
    @GetMapping("/campaigns")
    public List<WebCampaignResponseDto> campaigns() {
        return service.campaigns();
    }

    @RequireRole("ADMIN")
    @GetMapping("/commissions")
    public Page<WebCommissionResponseDto> commissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return service.commissions(page, size);
    }
}
