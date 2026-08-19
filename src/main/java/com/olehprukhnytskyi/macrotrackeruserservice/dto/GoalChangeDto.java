package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GoalChangeDto {
    private LocalDate lastChangedAt;
}
