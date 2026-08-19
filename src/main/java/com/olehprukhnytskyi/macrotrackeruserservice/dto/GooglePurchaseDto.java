package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GooglePurchaseDto {
    @NotBlank
    private String productId;

    @NotBlank
    private String purchaseToken;
}
