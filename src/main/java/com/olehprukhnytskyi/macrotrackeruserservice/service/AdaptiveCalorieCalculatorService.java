package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.exception.NotFoundException;
import com.olehprukhnytskyi.exception.error.UserErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieEvaluationRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieEvaluationRequestDto.WeightSampleDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserProfileRepository;
import com.olehprukhnytskyi.util.Goal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdaptiveCalorieCalculatorService {
    static final int ANALYSIS_DAYS = 21;
    static final int REQUIRED_LOGGED_DAYS = 7;
    static final int REQUIRED_WEIGHTS = 3;
    static final int REQUIRED_WEIGHT_SPAN_DAYS = 7;
    static final int IDEAL_DATA_DAYS = 14;
    private static final int GOAL_STABILITY_DAYS = 7;
    private static final int CONSERVATIVE_ADJUSTMENT = 50;
    private static final int STANDARD_ADJUSTMENT = 100;
    private static final int CALORIES_PER_KG = 7700;
    private static final int DAYS_PER_WEEK = 7;
    private static final int ABSOLUTE_MIN_COMPLETE_DAY_CALORIES = 800;
    private static final int MAX_LOGGED_CALORIES = 6000;
    private static final int MAX_RECENT_WEIGHT_GAP_DAYS = 7;
    private static final double BMR_COMPLETE_DAY_RATIO = 0.5;
    private static final double MAX_ADJACENT_WEIGHT_CHANGE_KG = 3.0;
    private static final double ANOMALOUS_CHANGE_PERCENT = 1.5;
    private static final double AGGRESSIVE_CHANGE_PERCENT = 1.0;
    private static final double TARGET_LOWER_TOLERANCE_PERCENT = 0.15;
    private static final double MAINTAIN_TOLERANCE_PERCENT = 0.25;

    private final UserProfileRepository userProfileRepository;
    private final UserProfileMapper profileMapper;
    private final GoalScheduleService goalScheduleService;

    @Transactional(readOnly = true)
    public AdaptiveCalorieRecommendationDto evaluate(
            Long userId, AdaptiveCalorieEvaluationRequestDto request) {
        UserProfile profile = userProfileRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        UserErrorCode.USER_PROFILE_NOT_FOUND, "Profile not found"));
        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(ANALYSIS_DAYS - 1L);
        List<WeightSampleDto> weights = normalizedWeights(request, cutoff, today);
        int spanDays = weightSpanDays(weights);
        Optional<LocalDate> lastGoalChange = goalScheduleService.lastGoalChange(userId);
        BigDecimal trend = regressionTrend(weights);
        BigDecimal currentWeight = currentWeight(profile, weights);
        BigDecimal targetTrend = WeeklyWeightChangePolicy.resolve(
                profile.getGoal(), profile.getWeeklyWeightChangeKg());
        LocalDate nextCheckIn = nextCheckIn(lastGoalChange, today);
        if (currentWeight.signum() <= 0) {
            int loggedDays = normalizedCalories(request, cutoff, today,
                    ABSOLUTE_MIN_COMPLETE_DAY_CALORIES).size();
            return base(profile, loggedDays, weights.size(), spanDays, trend)
                    .eligible(false)
                    .targetKgPerWeek(targetTrend)
                    .nextCheckInDate(nextCheckIn)
                    .status("PROFILE_DATA_REQUIRED")
                    .explanation("Current weight must be greater than zero before "
                            + "a calorie recommendation can be calculated.")
                    .blockers(List.of("Update your current weight in profile details"))
                    .build();
        }

        UserDetailsRequestDto calorieFloorProfile =
                profileMapper.toUserDetailsRequest(profile);
        calorieFloorProfile.setWeight(currentWeight.setScale(
                0, RoundingMode.HALF_UP).intValue());
        int minimumCompleteDayCalories = Math.max(
                ABSOLUTE_MIN_COMPLETE_DAY_CALORIES,
                (int) Math.ceil(CalorieCalculatorService.calculateBmrCalories(
                        calorieFloorProfile) * BMR_COMPLETE_DAY_RATIO));
        Map<LocalDate, BigDecimal> calories = normalizedCalories(
                request, cutoff, today, minimumCompleteDayCalories);
        int loggedDays = calories.size();
        List<String> blockers = buildBlockers(
                weights, spanDays, loggedDays, today, lastGoalChange);
        Integer maintenance = estimatedMaintenance(calories, trend, currentWeight);
        Integer weeksToGoal = estimatedWeeksToGoal(
                profile, currentWeight, targetTrend);
        LocalDate goalDate = weeksToGoal == null ? null : today.plusWeeks(weeksToGoal);

        if (!blockers.isEmpty()) {
            return base(profile, loggedDays, weights.size(), spanDays, trend)
                    .eligible(false)
                    .targetKgPerWeek(targetTrend)
                    .estimatedMaintenanceCalories(maintenance)
                    .estimatedWeeksToGoal(weeksToGoal)
                    .estimatedGoalDate(goalDate)
                    .nextCheckInDate(nextCheckIn)
                    .status("BUILDING_DATA")
                    .explanation("At least 7 days of consistent food and weight data "
                            + "are needed before adjusting calories.")
                    .blockers(blockers)
                    .build();
        }

        boolean conservative = spanDays < IDEAL_DATA_DAYS
                || loggedDays < IDEAL_DATA_DAYS;
        int adjustmentStep = conservative
                ? CONSERVATIVE_ADJUSTMENT : STANDARD_ADJUSTMENT;
        Decision decision = decide(profile.getGoal(), trend, targetTrend,
                currentWeight, adjustmentStep, conservative);
        int calorieFloor = CalorieCalculatorService.calculateCalorieFloor(
                calorieFloorProfile);
        if (decision.delta() < 0
                && profile.getCalories() + decision.delta() < calorieFloor) {
            decision = new Decision(0, "BMR_FLOOR",
                    "Calories are already at your safe BMR floor, so the plan is held.");
        }
        int suggestedCalories = profile.getCalories() + decision.delta();
        return base(profile, loggedDays, weights.size(), spanDays, trend)
                .eligible(true)
                .suggestedCalories(suggestedCalories)
                .calorieDelta(decision.delta())
                .targetKgPerWeek(targetTrend)
                .estimatedMaintenanceCalories(maintenance)
                .estimatedWeeksToGoal(weeksToGoal)
                .estimatedGoalDate(goalDate)
                .nextCheckInDate(nextCheckIn)
                .status(decision.status())
                .explanation(decision.explanation())
                .blockers(List.of())
                .build();
    }

    private AdaptiveCalorieRecommendationDto.AdaptiveCalorieRecommendationDtoBuilder base(
            UserProfile profile, int loggedDays, int weights, int spanDays,
            BigDecimal trend) {
        return AdaptiveCalorieRecommendationDto.builder()
                .loggedDays(loggedDays)
                .requiredLoggedDays(REQUIRED_LOGGED_DAYS)
                .weightEntries(weights)
                .requiredWeightEntries(REQUIRED_WEIGHTS)
                .weightSpanDays(spanDays)
                .currentCalories(profile.getCalories())
                .observedKgPerWeek(trend);
    }

    private List<String> buildBlockers(List<WeightSampleDto> weights,
                                       int spanDays, int loggedDays, LocalDate today,
                                       Optional<LocalDate> lastGoalChange) {
        List<String> blockers = new ArrayList<>();
        if (loggedDays < REQUIRED_LOGGED_DAYS) {
            blockers.add("Log food on " + (REQUIRED_LOGGED_DAYS - loggedDays)
                    + " more days");
        }
        if (weights.size() < REQUIRED_WEIGHTS) {
            blockers.add("Add " + (REQUIRED_WEIGHTS - weights.size())
                    + " more weight entries");
        }
        if (spanDays < REQUIRED_WEIGHT_SPAN_DAYS) {
            blockers.add("Keep tracking weight for "
                    + (REQUIRED_WEIGHT_SPAN_DAYS - spanDays) + " more days");
        }
        lastGoalChange
                .filter(date -> date.isAfter(today.minusDays(GOAL_STABILITY_DAYS)))
                .ifPresent(date -> blockers.add(
                        "Keep the current goal for 7 days before adapting it"));
        if (hasVolatileWeightEntry(weights)) {
            blockers.add("Recent weight entries are too volatile for a reliable trend");
        }
        if (hasStaleRecentWeightGap(weights)) {
            blockers.add("Add a few recent weight entries; the latest gap is over 7 days");
        }
        return blockers;
    }

    private Decision decide(Goal goal, BigDecimal trend, BigDecimal targetTrend,
                            BigDecimal currentWeight, int adjustmentStep,
                            boolean conservative) {
        double observedPercent = trend.doubleValue()
                / currentWeight.doubleValue() * 100;
        if (Math.abs(observedPercent) > ANOMALOUS_CHANGE_PERCENT) {
            return new Decision(0, "ANOMALOUS_CHANGE_HOLD",
                    "Rapid weight change is often water or glycogen. Keep the current "
                            + "plan so metabolism and recovery are not compromised.");
        }
        if (goal == null || goal == Goal.MAINTAIN) {
            return decideMaintenance(observedPercent, adjustmentStep);
        }

        int direction = goal == Goal.LOSE ? -1 : 1;
        double progressPercent = observedPercent * direction;
        if (progressPercent >= AGGRESSIVE_CHANGE_PERCENT) {
            int delta = conservative ? 0 : -direction * CONSERVATIVE_ADJUSTMENT;
            return new Decision(delta, "AGGRESSIVE_CHANGE",
                    "Weight is changing very quickly. Hold the plan for now; if fatigue "
                            + "or poor recovery appears, use the small 50 kcal adjustment.");
        }

        double targetPercent = Math.abs(targetTrend.doubleValue())
                / currentWeight.doubleValue() * 100;
        double lowerTarget = Math.max(0,
                targetPercent - TARGET_LOWER_TOLERANCE_PERCENT);
        if (progressPercent >= lowerTarget) {
            return new Decision(0, "ON_TRACK",
                    "Your weight trend is close to the target. Keep the current plan "
                            + "to preserve lean mass and avoid unnecessary changes.");
        }
        return new Decision(direction * adjustmentStep, "ADJUSTMENT_RECOMMENDED",
                "The observed trend is below the selected target. Adjust calories by "
                        + adjustmentStep + " kcal/day and reassess next week.");
    }

    private Decision decideMaintenance(double observedPercent, int adjustmentStep) {
        if (Math.abs(observedPercent) <= MAINTAIN_TOLERANCE_PERCENT) {
            return new Decision(0, "ON_TRACK",
                    "Weight is stable. Keep the current calorie target.");
        }
        int delta = observedPercent > 0 ? -adjustmentStep : adjustmentStep;
        return new Decision(delta, "ADJUSTMENT_RECOMMENDED",
                "Weight is drifting outside the maintenance range. Adjust calories by "
                        + Math.abs(delta) + " kcal/day and reassess next week.");
    }

    private List<WeightSampleDto> normalizedWeights(
            AdaptiveCalorieEvaluationRequestDto request,
            LocalDate cutoff, LocalDate today) {
        Map<LocalDate, BigDecimal> byDate = new TreeMap<>();
        if (request != null && request.getWeights() != null) {
            request.getWeights().stream()
                    .filter(sample -> sample != null && sample.getDate() != null
                            && sample.getWeight() != null
                            && sample.getWeight().signum() > 0
                            && !sample.getDate().isBefore(cutoff)
                            && !sample.getDate().isAfter(today))
                    .forEach(sample -> byDate.put(sample.getDate(), sample.getWeight()));
        }
        return byDate.entrySet().stream()
                .map(entry -> WeightSampleDto.builder()
                        .date(entry.getKey()).weight(entry.getValue()).build())
                .toList();
    }

    private Map<LocalDate, BigDecimal> normalizedCalories(
            AdaptiveCalorieEvaluationRequestDto request,
            LocalDate cutoff, LocalDate today, int minimumCompleteDayCalories) {
        Map<LocalDate, BigDecimal> byDate = new TreeMap<>();
        if (request != null && request.getSummaries() != null) {
            request.getSummaries().stream()
                    .filter(sample -> sample != null && sample.getDate() != null
                            && sample.getCalories() != null
                            && sample.getCalories().compareTo(
                                    BigDecimal.valueOf(minimumCompleteDayCalories)) >= 0
                            && sample.getCalories().compareTo(
                                    BigDecimal.valueOf(MAX_LOGGED_CALORIES)) <= 0
                            && !sample.getDate().isBefore(cutoff)
                            && !sample.getDate().isAfter(today))
                    .forEach(sample -> byDate.put(sample.getDate(), sample.getCalories()));
        }
        return byDate;
    }

    private int weightSpanDays(List<WeightSampleDto> weights) {
        return weights.size() < 2 ? 0 : (int) ChronoUnit.DAYS.between(
                weights.getFirst().getDate(), weights.getLast().getDate());
    }

    private BigDecimal regressionTrend(List<WeightSampleDto> weights) {
        if (weights.size() < 2) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        LocalDate start = weights.getFirst().getDate();
        double meanX = weights.stream()
                .mapToLong(item -> ChronoUnit.DAYS.between(start, item.getDate()))
                .average().orElse(0);
        double meanY = weights.stream()
                .mapToDouble(item -> item.getWeight().doubleValue())
                .average().orElse(0);
        double numerator = 0;
        double denominator = 0;
        for (WeightSampleDto weight : weights) {
            double x = ChronoUnit.DAYS.between(start, weight.getDate()) - meanX;
            numerator += x * (weight.getWeight().doubleValue() - meanY);
            denominator += x * x;
        }
        double weeklyTrend = denominator == 0 ? 0 : numerator / denominator * DAYS_PER_WEEK;
        return BigDecimal.valueOf(weeklyTrend).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean hasVolatileWeightEntry(List<WeightSampleDto> weights) {
        for (int index = 1; index < weights.size(); index++) {
            BigDecimal change = weights.get(index).getWeight()
                    .subtract(weights.get(index - 1).getWeight()).abs();
            if (change.compareTo(BigDecimal.valueOf(
                    MAX_ADJACENT_WEIGHT_CHANGE_KG)) > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean hasStaleRecentWeightGap(List<WeightSampleDto> weights) {
        if (weights.size() < 2) {
            return false;
        }
        LocalDate previous = weights.get(weights.size() - 2).getDate();
        LocalDate latest = weights.getLast().getDate();
        return ChronoUnit.DAYS.between(previous, latest)
                > MAX_RECENT_WEIGHT_GAP_DAYS;
    }

    private BigDecimal currentWeight(UserProfile profile, List<WeightSampleDto> weights) {
        return weights.isEmpty() ? BigDecimal.valueOf(profile.getWeight())
                : weights.getLast().getWeight();
    }

    private Integer estimatedMaintenance(Map<LocalDate, BigDecimal> calories,
                                         BigDecimal trend, BigDecimal currentWeight) {
        if (calories.isEmpty()) {
            return null;
        }
        double percent = Math.abs(trend.doubleValue()
                / currentWeight.doubleValue() * 100);
        if (percent > ANOMALOUS_CHANGE_PERCENT) {
            return null;
        }
        BigDecimal average = calories.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(calories.size()), 0, RoundingMode.HALF_UP);
        BigDecimal storedEnergy = trend.multiply(BigDecimal.valueOf(CALORIES_PER_KG))
                .divide(BigDecimal.valueOf(DAYS_PER_WEEK), 0, RoundingMode.HALF_UP);
        int estimate = average.subtract(storedEnergy).intValue();
        return estimate < 1000 || estimate > MAX_LOGGED_CALORIES
                ? null : (int) Math.round(estimate / 10.0) * 10;
    }

    private Integer estimatedWeeksToGoal(UserProfile profile, BigDecimal currentWeight,
                                         BigDecimal targetTrend) {
        if (profile.getGoalWeight() == null || targetTrend.signum() == 0) {
            return null;
        }
        BigDecimal distance = currentWeight
                .subtract(BigDecimal.valueOf(profile.getGoalWeight())).abs();
        return Math.max(1, (int) Math.ceil(distance.doubleValue()
                / targetTrend.abs().doubleValue()));
    }

    private LocalDate nextCheckIn(Optional<LocalDate> lastGoalChange, LocalDate today) {
        return lastGoalChange
                .filter(date -> date.isAfter(today.minusDays(GOAL_STABILITY_DAYS)))
                .map(date -> date.plusDays(GOAL_STABILITY_DAYS))
                .orElse(today.plusWeeks(1));
    }

    private record Decision(int delta, String status, String explanation) {
    }
}
