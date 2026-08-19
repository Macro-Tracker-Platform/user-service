package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoalScheduleDto {
    private Long id;
    @NotNull
    private DayOfWeek dayOfWeek;
    @Min(1)
    private int calories;
    @Min(0)
    private int protein;
    @Min(0)
    private int fat;
    @Min(0)
    private int carbohydrates;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
}
