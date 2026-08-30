package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BarcodeScanQuotaDto {
    private boolean allowed;
    private boolean unlimited;
    private Integer limit;
    private Integer remaining;
    private Instant resetAt;
}
