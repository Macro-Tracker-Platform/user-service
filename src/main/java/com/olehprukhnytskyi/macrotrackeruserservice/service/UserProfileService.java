package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;

public interface UserProfileService {
    UserDetailsResponseDto findDetailsByUserId(Long userId);

    GoalResponseDto findGoalByUserId(Long userId);
}
