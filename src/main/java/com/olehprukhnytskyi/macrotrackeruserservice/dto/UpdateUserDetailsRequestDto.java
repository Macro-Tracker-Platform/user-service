package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.ActivityLevel;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Gender;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Goal;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserDetailsRequestDto {
    @Positive
    private Integer age;

    @Positive
    private Integer weight;

    @Positive
    private Integer height;

    private Gender gender;

    private ActivityLevel activityLevel;

    private Goal goal;

    private boolean recalculate;
}
