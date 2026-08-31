package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FoodPhotoScanCreditDto {
    private boolean allowed;
    private boolean consumed;

    @JsonProperty("remaining_scans")
    private int remainingScans;
}
