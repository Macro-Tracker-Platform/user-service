package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
public class AcquisitionManagerRequestDto {
    @NotBlank
    @Size(max = 255)
    private String name;

    @Email
    @Size(max = 320)
    private String email;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    private BigDecimal commissionPercent;
}
