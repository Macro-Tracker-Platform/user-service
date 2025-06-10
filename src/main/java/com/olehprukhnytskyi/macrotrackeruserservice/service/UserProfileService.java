package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateUserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;

public interface UserProfileService {
    UserDetailsResponseDto findDetailsByUserId(Long userId);

    GoalResponseDto findGoalByUserId(Long userId);

    UserDetailsResponseDto updateUserDetails(UpdateUserDetailsRequestDto requestDto, Long userId);

    GoalResponseDto updateUserGoal(UpdateGoalRequestDto requestDto, Long userId);
}
