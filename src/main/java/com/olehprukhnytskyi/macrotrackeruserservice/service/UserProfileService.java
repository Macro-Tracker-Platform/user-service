package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.UserErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.client.GoalClient;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateUserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserDetailsProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserGoalProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper profileMapper;
    private final GoalClient goalClient;

    @Cacheable(value = "userDetails", key = "#userId")
    public UserDetailsResponseDto findDetailsByUserId(Long userId) {
        UserDetailsProjection detailsProjection = userProfileRepository.findDetailsByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("Profile not found for userId={}", userId);
                    return new NotFoundException(UserErrorCode.USER_PROFILE_NOT_FOUND,
                            "Profile not found");
                });
        return profileMapper.toDto(detailsProjection);
    }

    @Cacheable(value = "userGoals", key = "#userId")
    public GoalResponseDto findGoalByUserId(Long userId) {
        UserGoalProjection goalsProjection = userProfileRepository.findGoalsByUserId(userId)
                .orElseThrow(() -> {
                    log.warn("Profile goals not found for userId={}", userId);
                    return new NotFoundException(UserErrorCode.USER_PROFILE_NOT_FOUND,
                            "Profile not found");
                });
        return profileMapper.toDto(goalsProjection);
    }

    @Caching(evict = {
            @CacheEvict(value = "userDetails", key = "#userId"),
            @CacheEvict(value = "userGoals", key = "#userId",
                    condition = "#requestDto.isRecalculate()")
    })
    public UserDetailsResponseDto updateUserDetails(
            UpdateUserDetailsRequestDto requestDto, Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Profile not found for update, userId={}", userId);
                    return new NotFoundException(UserErrorCode.USER_PROFILE_NOT_FOUND,
                            "Profile not found");
                });
        if (requestDto.isRecalculate()) {
            GoalResponseDto calculatedGoal = goalClient.calculateGoal(
                    profileMapper.toUserDetailsRequest(requestDto));
            profileMapper.updateUserGoalFromDto(profile, calculatedGoal);
            log.info("User goal recalculated for userId={}", userId);
        }
        profileMapper.updateUserDetailsFromDto(profile, requestDto);
        userProfileRepository.save(profile);
        log.info("User profile updated for userId={}", userId);
        return profileMapper.toUserDetailsResponse(profile);
    }

    @CacheEvict(value = "userGoals", key = "#userId")
    public GoalResponseDto updateUserGoal(UpdateGoalRequestDto requestDto, Long userId) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Profile not found for goal update, userId={}", userId);
                    return new NotFoundException(UserErrorCode.USER_PROFILE_NOT_FOUND,
                            "Profile not found");
                });
        profileMapper.updateUserGoalFromDto(profile, profileMapper
                .toUserGoalResponse(requestDto));
        userProfileRepository.save(profile);
        log.info("User goal updated for userId={}", userId);
        return profileMapper.toUserGoalResponse(profile);
    }
}
