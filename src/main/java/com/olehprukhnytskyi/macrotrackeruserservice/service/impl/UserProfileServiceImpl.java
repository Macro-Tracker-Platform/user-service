package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.client.GoalClient;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateUserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserDetailsProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserGoalProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserProfileRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper profileMapper;
    private final GoalClient goalClient;

    @Cacheable(value = "userDetails", key = "#userId")
    @Override
    public UserDetailsResponseDto findDetailsByUserId(Long userId) {
        UserDetailsProjection detailsProjection = userProfileRepository.findDetailsByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return profileMapper.toDto(detailsProjection);
    }

    @Cacheable(value = "userGoals", key = "#userId")
    @Override
    public GoalResponseDto findGoalByUserId(Long userId) {
        UserGoalProjection goalsProjection = userProfileRepository.findGoalsByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return profileMapper.toDto(goalsProjection);
    }

    @CacheEvict(value = "userDetails", key = "#userId")
    @Override
    public UserDetailsResponseDto updateUserDetails(
            UpdateUserDetailsRequestDto requestDto, Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        if (requestDto.isRecalculate()) {
            GoalResponseDto calculatedGoal = goalClient.calculateGoal(
                    profileMapper.toUserDetailsRequest(requestDto));
            profileMapper.updateUserGoalFromDto(profile, calculatedGoal);
        }
        profileMapper.updateUserDetailsFromDto(profile, requestDto);
        userProfileRepository.save(profile);
        return profileMapper.toUserDetailsResponse(profile);
    }

    @CacheEvict(value = "userGoals", key = "#userId")
    @Override
    public GoalResponseDto updateUserGoal(UpdateGoalRequestDto requestDto, Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        profileMapper.updateUserGoalFromDto(profile, profileMapper
                .toUserGoalResponse(requestDto));
        userProfileRepository.save(profile);
        return profileMapper.toUserGoalResponse(profile);
    }
}
