package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import lombok.Data;

@Data
public class UserProfileResponseDto {
    private Integer calories;
    private Integer carbohydrates;
    private Integer fat;
    private Integer protein;
}
