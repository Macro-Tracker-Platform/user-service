package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieEvaluationRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieEvaluationRequestDto.DailyCalorieSampleDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieEvaluationRequestDto.WeightSampleDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AdaptiveCalorieRecommendationDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserProfileRepository;
import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptiveCalorieCalculatorServiceTest {
    private static final Long USER_ID = 1L;
    private static final BigDecimal CURRENT_WEIGHT = BigDecimal.valueOf(80);

    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private GoalScheduleService goalScheduleService;
    @InjectMocks
    private AdaptiveCalorieCalculatorService service;

    private UserProfile profile;

    @BeforeEach
    void setUp() {
        profile = profile(Goal.LOSE, "-0.40", 2200);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(goalScheduleService.lastGoalChange(USER_ID)).thenReturn(Optional.empty());
        stubCalorieFloorProfile();
    }

    @Test
    void matureLossWindowUsesOneHundredCalorieClamp() {
        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(15, -0.10));

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getCalorieDelta()).isEqualTo(-100);
        assertThat(result.getStatus()).isEqualTo("ADJUSTMENT_RECOMMENDED");
        assertThat(result.getObservedKgPerWeek()).isEqualByComparingTo("-0.07");
    }

    @Test
    void firstReportUsesConservativeFiftyCalorieClamp() {
        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(8, -0.10));

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getCalorieDelta()).isEqualTo(-50);
    }

    @Test
    void anomalousLossAboveOneAndHalfPercentHoldsCalories() {
        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(15, -1.90));

        assertThat(result.getCalorieDelta()).isZero();
        assertThat(result.getStatus()).isEqualTo("ANOMALOUS_CHANGE_HOLD");
        assertThat(result.getExplanation()).contains("water or glycogen");
    }

    @Test
    void aggressiveLossUsesSmallPositiveAdjustment() {
        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(15, -1.30));

        assertThat(result.getCalorieDelta()).isEqualTo(50);
        assertThat(result.getStatus()).isEqualTo("AGGRESSIVE_CHANGE");
        assertThat(result.getExplanation()).contains("Adjust calories by 50");
    }

    @Test
    void aggressiveFirstReportHoldsWithoutSuggestingAdjustment() {
        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(8, -2.00));

        assertThat(result.getCalorieDelta()).isZero();
        assertThat(result.getStatus()).isEqualTo("AGGRESSIVE_CHANGE");
        assertThat(result.getExplanation()).contains("Keep calories unchanged");
    }

    @Test
    void optimalLossPaceKeepsCurrentCalories() {
        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(15, -0.48));

        assertThat(result.getCalorieDelta()).isZero();
        assertThat(result.getStatus()).isEqualTo("ON_TRACK");
    }

    @Test
    void trendUsesRegressionInsteadOfFirstToLastDifference() {
        AdaptiveCalorieEvaluationRequestDto request = request(15, 0);
        request.getWeights().getFirst().setWeight(BigDecimal.valueOf(81));
        request.getWeights().getLast().setWeight(BigDecimal.valueOf(79));

        AdaptiveCalorieRecommendationDto result = service.evaluate(USER_ID, request);

        assertThat(result.getObservedKgPerWeek()).isEqualByComparingTo("-0.45");
        assertThat(result.getStatus()).isEqualTo("ON_TRACK");
    }

    @Test
    void slowGainAddsCalories() {
        profile = profile(Goal.GAIN, "0.40", 2600);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        stubCalorieFloorProfile();

        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(15, 0.10));

        assertThat(result.getCalorieDelta()).isEqualTo(100);
        assertThat(result.getSuggestedCalories()).isEqualTo(2700);
    }

    @Test
    void aggressiveGainUsesSmallNegativeAdjustment() {
        profile = profile(Goal.GAIN, "0.40", 2600);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        stubCalorieFloorProfile();

        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(15, 1.30));

        assertThat(result.getCalorieDelta()).isEqualTo(-50);
        assertThat(result.getStatus()).isEqualTo("AGGRESSIVE_CHANGE");
    }

    @Test
    void bmrFloorPreventsFurtherCalorieReduction() {
        profile = profile(Goal.LOSE, "-0.40", 1400);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        stubCalorieFloorProfile();

        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(15, -0.10));

        assertThat(result.getCalorieDelta()).isZero();
        assertThat(result.getSuggestedCalories()).isEqualTo(1400);
        assertThat(result.getStatus()).isEqualTo("BMR_FLOOR");
    }

    @Test
    void fewerThanSevenDaysBuildsDataWithoutRecommendation() {
        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(6, -0.10));

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getSuggestedCalories()).isNull();
        assertThat(result.getBlockers()).isNotEmpty();
    }

    @Test
    void partialDaysBelowHalfBmrAreIgnored() {
        AdaptiveCalorieEvaluationRequestDto request = request(8, -0.10);
        request.getSummaries().forEach(sample ->
                sample.setCalories(BigDecimal.valueOf(900)));

        AdaptiveCalorieRecommendationDto result = service.evaluate(USER_ID, request);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getLoggedDays()).isZero();
        assertThat(result.getBlockers()).anyMatch(value -> value.contains("Log food"));
    }

    @Test
    void zeroStoredWeightReturnsProfileDataRequiredInsteadOfCalculating() {
        profile.setWeight(0);
        AdaptiveCalorieEvaluationRequestDto request = request(8, -0.10);
        request.setWeights(List.of());

        AdaptiveCalorieRecommendationDto result = service.evaluate(USER_ID, request);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getStatus()).isEqualTo("PROFILE_DATA_REQUIRED");
        assertThat(result.getEstimatedMaintenanceCalories()).isNull();
    }

    @Test
    void gapOverSevenDaysBetweenLatestWeightsBuildsMoreData() {
        AdaptiveCalorieEvaluationRequestDto request = request(21, -0.10);
        List<WeightSampleDto> weights = request.getWeights();
        request.setWeights(List.of(
                weights.get(0), weights.get(1), weights.get(2), weights.getLast()));

        AdaptiveCalorieRecommendationDto result = service.evaluate(USER_ID, request);

        assertThat(result.isEligible()).isFalse();
        assertThat(result.getStatus()).isEqualTo("BUILDING_DATA");
        assertThat(result.getBlockers()).anyMatch(value -> value.contains("latest gap"));
    }

    @Test
    void reachedLossGoalRecommendsMaintenanceBeforeNormalEligibilityChecks() {
        profile.setGoalWeight(80);
        AdaptiveCalorieEvaluationRequestDto request = request(8, -0.10);
        request.setSummaries(List.of());

        AdaptiveCalorieRecommendationDto result = service.evaluate(USER_ID, request);

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getStatus()).isEqualTo("GOAL_REACHED");
        assertThat(result.getTargetKgPerWeek()).isZero();
        assertThat(result.getEstimatedWeeksToGoal()).isZero();
        assertThat(result.getSuggestedCalories()).isGreaterThan(profile.getCalories());
        assertThat(result.getEstimatedMaintenanceCalories())
                .isEqualTo(result.getSuggestedCalories());
        assertThat(result.getExplanation()).contains("reached your target weight");
    }

    @Test
    void reachedGainGoalAlsoRecommendsMaintenance() {
        profile = profile(Goal.GAIN, "0.40", 2600);
        profile.setGoalWeight(80);
        when(userProfileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        stubCalorieFloorProfile();

        AdaptiveCalorieRecommendationDto result = service.evaluate(
                USER_ID, request(8, 0.10));

        assertThat(result.isEligible()).isTrue();
        assertThat(result.getStatus()).isEqualTo("GOAL_REACHED");
        assertThat(result.getTargetKgPerWeek()).isZero();
        assertThat(result.getEstimatedWeeksToGoal()).isZero();
        assertThat(result.getSuggestedCalories()).isNotNull();
    }

    private AdaptiveCalorieEvaluationRequestDto request(int days, double weeklyTrend) {
        LocalDate today = LocalDate.now();
        List<DailyCalorieSampleDto> summaries = new ArrayList<>();
        List<WeightSampleDto> weights = new ArrayList<>();
        for (int daysAgo = days - 1; daysAgo >= 0; daysAgo--) {
            LocalDate date = today.minusDays(daysAgo);
            summaries.add(DailyCalorieSampleDto.builder()
                    .date(date).calories(BigDecimal.valueOf(2100)).build());
            BigDecimal weight = CURRENT_WEIGHT.subtract(BigDecimal.valueOf(
                    weeklyTrend / 7.0 * daysAgo));
            weights.add(WeightSampleDto.builder().date(date)
                    .weight(weight.setScale(3, RoundingMode.HALF_UP)).build());
        }
        return AdaptiveCalorieEvaluationRequestDto.builder()
                .summaries(summaries).weights(weights).build();
    }

    private UserProfile profile(Goal goal, String weeklyRate, int calories) {
        UserProfile result = new UserProfile();
        result.setAge(30);
        result.setWeight(80);
        result.setGoalWeight(goal == Goal.GAIN ? 85 : 70);
        result.setWeeklyWeightChangeKg(new BigDecimal(weeklyRate));
        result.setHeight(180);
        result.setGender(Gender.MALE);
        result.setActivityLevel(ActivityLevel.MODERATELY_ACTIVE);
        result.setBodyType(BodyType.NORMAL);
        result.setGoal(goal);
        result.setCalories(calories);
        return result;
    }

    private void stubCalorieFloorProfile() {
        lenient().when(profileMapper.toUserDetailsRequest(profile)).thenReturn(
                UserDetailsRequestDto.builder()
                        .age(profile.getAge())
                        .weight(profile.getWeight())
                        .height(profile.getHeight())
                        .gender(profile.getGender())
                        .activityLevel(profile.getActivityLevel())
                        .bodyType(profile.getBodyType())
                        .goal(profile.getGoal())
                        .weeklyWeightChangeKg(profile.getWeeklyWeightChangeKg())
                        .build());
    }
}
