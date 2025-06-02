package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserDetailsProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserGoalProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserProfileRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper profileMapper;

    @Override
    public UserDetailsResponseDto findDetailsByUserId(Long userId) {
        UserDetailsProjection detailsProjection = userProfileRepository.findDetailsByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return profileMapper.toDto(detailsProjection);
    }

    @Override
    public GoalResponseDto findGoalByUserId(Long userId) {
        UserGoalProjection goalsProjection = userProfileRepository.findGoalsByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return profileMapper.toDto(goalsProjection);
    }
}
