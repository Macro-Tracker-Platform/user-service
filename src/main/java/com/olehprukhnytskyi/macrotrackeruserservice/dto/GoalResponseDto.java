package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalResponseDto {
    private int calories;
    private int protein;
    private int fat;
    private int carbohydrates;
}
