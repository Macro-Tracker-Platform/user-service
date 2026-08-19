package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalScheduleDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.GoalHistory;
import com.olehprukhnytskyi.macrotrackeruserservice.model.GoalSchedule;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.GoalHistoryRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.GoalScheduleRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserProfileRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class GoalScheduleService {
    private static final int MAX_KCAL_DIFFERENCE = 150;
    private final GoalScheduleRepository scheduleRepository;
    private final GoalHistoryRepository historyRepository;
    private final UserProfileRepository profileRepository;
    private final SubscriptionService subscriptionService;

    @Transactional(readOnly = true)
    public GoalResponseDto resolve(Long userId, LocalDate date) {
        UserProfile profile = requireProfile(userId);
        return scheduleRepository.resolve(userId, date.getDayOfWeek(), date)
                .map(schedule -> toGoal(schedule, profile))
                .orElseGet(() -> historyRepository.resolve(userId, date)
                        .map(history -> toGoal(history, profile))
                        .orElseGet(() -> toGoal(profile)));
    }

    @Transactional(readOnly = true)
    public List<GoalScheduleDto> getActive(Long userId) {
        requirePro(userId);
        return scheduleRepository.findByUserIdAndEffectiveToIsNullOrderByDayOfWeek(userId)
                .stream().map(this::toDto).toList();
    }

    public Optional<LocalDate> lastGoalChange(Long userId) {
        return historyRepository.findFirstByUserIdOrderByEffectiveFromDesc(userId)
                .map(GoalHistory::getEffectiveFrom)
                .filter(date -> date.isAfter(LocalDate.of(1970, 1, 1)));
    }

    @Transactional
    public GoalScheduleDto put(Long userId, DayOfWeek dayOfWeek, GoalScheduleDto request) {
        requirePro(userId);
        validateMacros(request.getCalories(), request.getProtein(), request.getFat(),
                request.getCarbohydrates());
        LocalDate effectiveFrom = request.getEffectiveFrom() == null
                ? LocalDate.now() : request.getEffectiveFrom();
        scheduleRepository
                .findFirstByUserIdAndDayOfWeekAndEffectiveToIsNullOrderByEffectiveFromDesc(
                        userId, dayOfWeek)
                .ifPresent(current -> {
                    current.setEffectiveTo(effectiveFrom.minusDays(1));
                    scheduleRepository.save(current);
                });
        GoalSchedule schedule = GoalSchedule.builder()
                .userId(userId)
                .dayOfWeek(dayOfWeek)
                .calories(request.getCalories())
                .protein(request.getProtein())
                .fat(request.getFat())
                .carbohydrates(request.getCarbohydrates())
                .effectiveFrom(effectiveFrom)
                .build();
        return toDto(scheduleRepository.save(schedule));
    }

    @Transactional
    public void delete(Long userId, DayOfWeek dayOfWeek) {
        requirePro(userId);
        scheduleRepository
                .findFirstByUserIdAndDayOfWeekAndEffectiveToIsNullOrderByEffectiveFromDesc(
                        userId, dayOfWeek)
                .ifPresent(current -> {
                    current.setEffectiveTo(LocalDate.now().minusDays(1));
                    scheduleRepository.save(current);
                });
    }

    @Transactional
    public void snapshotDefaultBeforeChange(Long userId) {
        LocalDate today = LocalDate.now();
        GoalHistory current = historyRepository
                .findFirstByUserIdAndEffectiveToIsNullOrderByEffectiveFromDesc(userId)
                .orElseGet(() -> {
                    UserProfile profile = requireProfile(userId);
                    return GoalHistory.builder().userId(userId)
                            .calories(profile.getCalories()).protein(profile.getProtein())
                            .fat(profile.getFat()).carbohydrates(profile.getCarbohydrates())
                            .effectiveFrom(LocalDate.of(1970, 1, 1)).build();
                });
        current.setEffectiveTo(today.minusDays(1));
        historyRepository.save(current);
    }

    @Transactional
    public void snapshotDefaultAfterChange(Long userId) {
        UserProfile profile = requireProfile(userId);
        historyRepository.save(GoalHistory.builder().userId(userId)
                .calories(profile.getCalories()).protein(profile.getProtein())
                .fat(profile.getFat()).carbohydrates(profile.getCarbohydrates())
                .effectiveFrom(LocalDate.now()).build());
    }

    public void validateMacros(int calories, int protein, int fat, int carbohydrates) {
        int macroCalories = protein * 4 + carbohydrates * 4 + fat * 9;
        if (Math.abs(calories - macroCalories) > MAX_KCAL_DIFFERENCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Calories must be consistent with protein, fat and carbohydrate targets");
        }
    }

    private void requirePro(Long userId) {
        EntitlementResponseDto entitlement = subscriptionService.getEntitlement(userId, null);
        if (entitlement.getFeatures() == null
                || !entitlement.getFeatures().isWeekdayGoals()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Weekday goals require MacroTracker Pro");
        }
    }

    private UserProfile requireProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Profile not found"));
    }

    private GoalResponseDto toGoal(GoalSchedule source, UserProfile profile) {
        return GoalResponseDto.builder().calories(source.getCalories())
                .protein(source.getProtein()).fat(source.getFat())
                .carbohydrates(source.getCarbohydrates())
                .waterGoalMl(profile.getWaterGoalMl()).waterGoalMode(profile.getWaterGoalMode())
                .build();
    }

    private GoalResponseDto toGoal(GoalHistory source, UserProfile profile) {
        return GoalResponseDto.builder().calories(source.getCalories())
                .protein(source.getProtein()).fat(source.getFat())
                .carbohydrates(source.getCarbohydrates())
                .waterGoalMl(profile.getWaterGoalMl()).waterGoalMode(profile.getWaterGoalMode())
                .build();
    }

    private GoalResponseDto toGoal(UserProfile source) {
        return GoalResponseDto.builder().calories(source.getCalories())
                .protein(source.getProtein()).fat(source.getFat())
                .carbohydrates(source.getCarbohydrates())
                .waterGoalMl(source.getWaterGoalMl()).waterGoalMode(source.getWaterGoalMode())
                .build();
    }

    private GoalScheduleDto toDto(GoalSchedule source) {
        return GoalScheduleDto.builder().id(source.getId()).dayOfWeek(source.getDayOfWeek())
                .calories(source.getCalories()).protein(source.getProtein())
                .fat(source.getFat()).carbohydrates(source.getCarbohydrates())
                .effectiveFrom(source.getEffectiveFrom()).effectiveTo(source.getEffectiveTo())
                .build();
    }
}
