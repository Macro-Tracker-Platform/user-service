package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PromoCodeAttributionResponseDto {
    private String code;
    private PromoAcquisitionType acquisitionType;
    private String partnerName;
    private Long acquisitionManagerId;
    private String acquisitionManagerName;
    private BigDecimal managerCommissionPercent;
}
