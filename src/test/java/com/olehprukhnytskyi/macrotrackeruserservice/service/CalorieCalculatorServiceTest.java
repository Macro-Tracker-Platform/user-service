package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CalorieCalculatorServiceTest {
    @Test
    @DisplayName("When goal is calculated, should include rounded water goal")
    void calculateGoal_whenCalled_shouldIncludeRoundedWaterGoal() {
        // Given
        UserDetailsRequestDto details = UserDetailsRequestDto.builder()
                .age(30)
                .weight(71)
                .height(180)
                .gender(Gender.MALE)
                .activityLevel(ActivityLevel.MODERATELY_ACTIVE)
                .goal(Goal.MAINTAIN)
                .bodyType(BodyType.NORMAL)
                .build();

        // When
        GoalResponseDto result = CalorieCalculatorService.calculateGoal(details);

        // Then
        assertThat(result.getWaterGoalMl()).isEqualTo(2500);
    }
}
