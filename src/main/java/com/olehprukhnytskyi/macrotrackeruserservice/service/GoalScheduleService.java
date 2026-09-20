package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.UserErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.EffectiveGoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.EntitlementResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalScheduleDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalSource;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
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
        return resolveEffective(userId, date).getGoal();
    }

    @Transactional(readOnly = true)
    public EffectiveGoalResponseDto resolveEffective(Long userId, LocalDate date) {
        UserProfile profile = requireProfile(userId);
        GoalResponseDto recommended = toGoal(profile);
        Optional<GoalHistory> custom = historyRepository.resolve(userId, date);
        GoalResponseDto baseGoal = custom.map(history -> toGoal(history, profile))
                .orElse(recommended);
        GoalSource baseSource = custom.isPresent()
                ? GoalSource.CUSTOM : GoalSource.RECOMMENDED;
        Optional<GoalSchedule> schedule = scheduleRepository.resolve(
                userId, date.getDayOfWeek(), date);
        return EffectiveGoalResponseDto.builder()
                .goal(schedule.map(value -> toGoal(value, profile)).orElse(baseGoal))
                .source(schedule.isPresent() ? GoalSource.SCHEDULE : baseSource)
                .baseGoal(baseGoal)
                .baseSource(baseSource)
                .recommendedGoal(recommended)
                .scheduleDay(schedule.map(GoalSchedule::getDayOfWeek).orElse(null))
                .build();
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
    public GoalResponseDto setCustom(Long userId, UpdateGoalRequestDto request) {
        LocalDate today = LocalDate.now();
        UserProfile profile = requireProfile(userId);
        Optional<GoalHistory> current = historyRepository
                .findFirstByUserIdAndEffectiveToIsNullOrderByEffectiveFromDesc(userId);
        GoalHistory baseline = current.orElseGet(() -> fromProfile(profile));
        GoalHistory next = customGoal(userId, today, request, baseline);
        validateMacros(next.getCalories(), next.getProtein(), next.getFat(),
                next.getCarbohydrates());
        current.ifPresent(value -> closeOrRemove(value, today));
        return toGoal(historyRepository.save(next), profile);
    }

    @Transactional
    public GoalResponseDto useRecommended(Long userId) {
        LocalDate today = LocalDate.now();
        historyRepository.findFirstByUserIdAndEffectiveToIsNullOrderByEffectiveFromDesc(userId)
                .ifPresent(value -> closeOrRemove(value, today));
        return toGoal(requireProfile(userId));
    }

    public void validateMacros(int calories, int protein, int fat, int carbohydrates) {
        int macroCalories = protein * 4 + carbohydrates * 4 + fat * 9;
        if (Math.abs(calories - macroCalories) > MAX_KCAL_DIFFERENCE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Calories must be consistent with protein, fat and carbohydrate targets");
        }
    }

    private void requirePro(Long userId) {
        EntitlementResponseDto entitlement = subscriptionService.getEntitlement(userId);
        if (entitlement.getFeatures() == null
                || !entitlement.getFeatures().isWeekdayGoals()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Weekday goals require MacroTracker Pro");
        }
    }

    private UserProfile requireProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(UserErrorCode.USER_PROFILE_NOT_FOUND,
                        "Profile not found"));
    }

    private void closeOrRemove(GoalHistory current, LocalDate today) {
        if (current.getEffectiveFrom().isBefore(today)) {
            current.setEffectiveTo(today.minusDays(1));
            historyRepository.save(current);
        } else {
            historyRepository.delete(current);
        }
    }

    private GoalHistory fromProfile(UserProfile profile) {
        return GoalHistory.builder()
                .calories(profile.getCalories()).protein(profile.getProtein())
                .fat(profile.getFat()).carbohydrates(profile.getCarbohydrates())
                .build();
    }

    private GoalHistory customGoal(Long userId, LocalDate date, UpdateGoalRequestDto request,
                                   GoalHistory baseline) {
        UpdateGoalRequestDto changes = request == null ? new UpdateGoalRequestDto() : request;
        return GoalHistory.builder().userId(userId)
                .calories(orDefault(changes.getCalories(), baseline.getCalories()))
                .protein(orDefault(changes.getProtein(), baseline.getProtein()))
                .fat(orDefault(changes.getFat(), baseline.getFat()))
                .carbohydrates(orDefault(changes.getCarbohydrates(),
                        baseline.getCarbohydrates()))
                .effectiveFrom(date).build();
    }

    private int orDefault(Integer value, Integer fallback) {
        return value == null ? fallback : value;
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
