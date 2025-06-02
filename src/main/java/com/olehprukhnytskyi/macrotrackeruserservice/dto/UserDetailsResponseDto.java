package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.olehprukhnytskyi.macrotrackeruserservice.util.ActivityLevel;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Gender;
import com.olehprukhnytskyi.macrotrackeruserservice.util.Goal;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDetailsResponseDto {
    private Integer age;
    private Integer weight;
    private Integer height;
    private Gender gender;
    private ActivityLevel activityLevel;
    private Goal goal;
}
