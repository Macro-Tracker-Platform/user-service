package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.UserErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateUserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateWaterGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserDetailsProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserGoalProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserProfileRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WaterGoalMode;
import com.olehprukhnytskyi.util.Goal;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {
    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper profileMapper;
    private final GoalScheduleService goalScheduleService;

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
    @Transactional
    public UserDetailsResponseDto updateUserDetails(
            UpdateUserDetailsRequestDto requestDto, Long userId) {
        UserProfile profile = findProfile(userId, "update");
        Goal effectiveGoal = requestDto.getGoal() == null ? profile.getGoal()
                : requestDto.getGoal();
        BigDecimal requestedChange = requestDto.getWeeklyWeightChangeKg() == null
                ? profile.getWeeklyWeightChangeKg() : requestDto.getWeeklyWeightChangeKg();
        requestDto.setWeeklyWeightChangeKg(
                WeeklyWeightChangePolicy.resolve(effectiveGoal, requestedChange));
        if (requestDto.isRecalculate()) {
            GoalResponseDto calculatedGoal = CalorieCalculatorService.calculateGoal(
                    profileMapper.toUserDetailsRequest(requestDto));
            profileMapper.updateUserGoalFromDto(profile, calculatedGoal);
            if (isAutomaticWaterGoal(profile)) {
                profile.setWaterGoalMl(calculatedGoal.getWaterGoalMl());
                profile.setWaterGoalMode(WaterGoalMode.AUTO);
            }
            log.info("User goal recalculated for userId={}", userId);
        }
        profileMapper.updateUserDetailsFromDto(profile, requestDto);
        userProfileRepository.save(profile);
        log.info("User profile updated for userId={}", userId);
        return profileMapper.toUserDetailsResponse(profile);
    }

    @CacheEvict(value = "userGoals", key = "#userId")
    @Transactional
    public GoalResponseDto updateUserGoal(UpdateGoalRequestDto requestDto, Long userId) {
        UserProfile profile = findProfile(userId, "goal update");
        if (goalScheduleService != null) {
            goalScheduleService.snapshotDefaultBeforeChange(userId);
        }
        profileMapper.updateUserGoalFromDto(profile, profileMapper
                .toUserGoalResponse(requestDto));
        if (goalScheduleService != null) {
            goalScheduleService.validateMacros(profile.getCalories(), profile.getProtein(),
                    profile.getFat(), profile.getCarbohydrates());
        }
        userProfileRepository.save(profile);
        if (goalScheduleService != null) {
            goalScheduleService.snapshotDefaultAfterChange(userId);
        }
        log.info("User goal updated for userId={}", userId);
        return profileMapper.toUserGoalResponse(profile);
    }

    @CacheEvict(value = "userGoals", key = "#userId")
    @Transactional
    public GoalResponseDto updateWaterGoal(UpdateWaterGoalRequestDto requestDto, Long userId) {
        UserProfile profile = findProfile(userId, "water goal update");
        profile.setWaterGoalMl(requestDto.getWaterGoalMl());
        profile.setWaterGoalMode(WaterGoalMode.CUSTOM);
        userProfileRepository.save(profile);
        log.info("Custom water goal updated for userId={}", userId);
        return profileMapper.toUserGoalResponse(profile);
    }

    @CacheEvict(value = "userGoals", key = "#userId")
    @Transactional
    public GoalResponseDto resetWaterGoal(Long userId) {
        UserProfile profile = findProfile(userId, "water goal reset");
        GoalResponseDto calculatedGoal = CalorieCalculatorService.calculateGoal(
                profileMapper.toUserDetailsRequest(profile));
        profile.setWaterGoalMl(calculatedGoal.getWaterGoalMl());
        profile.setWaterGoalMode(WaterGoalMode.AUTO);
        userProfileRepository.save(profile);
        log.info("Water goal reset to automatic for userId={}", userId);
        return profileMapper.toUserGoalResponse(profile);
    }

    private UserProfile findProfile(Long userId, String operation) {
        return userProfileRepository.findById(userId)
                .orElseThrow(() -> {
                    log.warn("Profile not found for {}, userId={}", operation, userId);
                    return new NotFoundException(UserErrorCode.USER_PROFILE_NOT_FOUND,
                            "Profile not found");
                });
    }

    private boolean isAutomaticWaterGoal(UserProfile profile) {
        return profile.getWaterGoalMode() == null
               || profile.getWaterGoalMode() == WaterGoalMode.AUTO;
    }
}
