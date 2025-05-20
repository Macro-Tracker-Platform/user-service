package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.ActivityLevel;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Gender;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Goal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsRequestDto {
    @NotNull
    @Positive
    private Integer age;

    @NotNull
    @Positive
    private Integer weight;

    @NotNull
    @Positive
    private Integer height;

    @NotNull
    private Gender gender;

    @NotNull
    private ActivityLevel activityLevel;

    @NotNull
    private Goal goal;
}
