package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromoCodeAttributionRequestDto {
    @NotNull
    private PromoAcquisitionType acquisitionType;

    private Long acquisitionManagerId;

    @Size(max = 255)
    private String partnerName;
}
