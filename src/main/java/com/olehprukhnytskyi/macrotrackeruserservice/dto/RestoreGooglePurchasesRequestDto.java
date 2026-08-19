package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class RestoreGooglePurchasesRequestDto {
    @Valid
    @NotEmpty
    private List<GooglePurchaseDto> purchases;
}
