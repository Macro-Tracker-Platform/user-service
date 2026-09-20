package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.DayOfWeek;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Effective nutrition goal and its source")
public class EffectiveGoalResponseDto {
    private GoalResponseDto goal;
    private GoalSource source;
    private GoalResponseDto baseGoal;
    private GoalSource baseSource;
    private GoalResponseDto recommendedGoal;
    private DayOfWeek scheduleDay;
}
