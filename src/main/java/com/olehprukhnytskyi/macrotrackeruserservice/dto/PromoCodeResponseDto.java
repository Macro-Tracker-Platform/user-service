package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PromoCodeResponseDto {
    private String code;
    private Integer discountPercent;
    private String monthlyOfferId;
    private String yearlyOfferId;
}
