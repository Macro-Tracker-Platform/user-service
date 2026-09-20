package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.EffectiveGoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalSource;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.GoalHistory;
import com.olehprukhnytskyi.macrotrackeruserservice.model.GoalSchedule;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.GoalHistoryRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.GoalScheduleRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserProfileRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.WaterGoalMode;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GoalScheduleServiceTest {
    private static final long USER_ID = 7L;

    @Mock
    private GoalScheduleRepository scheduleRepository;
    @Mock
    private GoalHistoryRepository historyRepository;
    @Mock
    private UserProfileRepository profileRepository;
    @Mock
    private SubscriptionService subscriptionService;
    @InjectMocks
    private GoalScheduleService service;

    @Test
    void resolveEffective_prefersScheduleAndExposesCustomBaseAndRecommendation() {
        LocalDate date = LocalDate.of(2026, 9, 21);
        final UserProfile profile = profile(1700);
        GoalHistory custom = history(5000, date.minusDays(10));
        GoalSchedule schedule = GoalSchedule.builder()
                .userId(USER_ID).dayOfWeek(date.getDayOfWeek())
                .calories(2300).protein(150).fat(60).carbohydrates(290)
                .effectiveFrom(date.minusDays(3)).build();
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(historyRepository.resolve(USER_ID, date)).thenReturn(Optional.of(custom));
        when(scheduleRepository.resolve(USER_ID, date.getDayOfWeek(), date))
                .thenReturn(Optional.of(schedule));

        EffectiveGoalResponseDto result = service.resolveEffective(USER_ID, date);

        assertThat(result.getSource()).isEqualTo(GoalSource.SCHEDULE);
        assertThat(result.getGoal().getCalories()).isEqualTo(2300);
        assertThat(result.getBaseSource()).isEqualTo(GoalSource.CUSTOM);
        assertThat(result.getBaseGoal().getCalories()).isEqualTo(5000);
        assertThat(result.getRecommendedGoal().getCalories()).isEqualTo(1700);
        assertThat(result.getScheduleDay()).isEqualTo(date.getDayOfWeek());
    }

    @Test
    void setCustom_mergesPartialRequestAndClosesPreviousActiveGoal() {
        LocalDate today = LocalDate.now();
        final UserProfile profile = profile(1700);
        GoalHistory current = history(2000, today.minusDays(10));
        current.setProtein(100);
        current.setFat(50);
        current.setCarbohydrates(287);
        UpdateGoalRequestDto request = new UpdateGoalRequestDto();
        request.setProtein(110);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(historyRepository.findFirstByUserIdAndEffectiveToIsNullOrderByEffectiveFromDesc(
                USER_ID)).thenReturn(Optional.of(current));
        when(historyRepository.save(any(GoalHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GoalResponseDto result = service.setCustom(USER_ID, request);

        assertThat(result.getCalories()).isEqualTo(2000);
        assertThat(result.getProtein()).isEqualTo(110);
        assertThat(current.getEffectiveTo()).isEqualTo(today.minusDays(1));
        ArgumentCaptor<GoalHistory> captor = ArgumentCaptor.forClass(GoalHistory.class);
        verify(historyRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        GoalHistory saved = captor.getAllValues().get(1);
        assertThat(saved.getEffectiveFrom()).isEqualTo(today);
        assertThat(saved.getCalories()).isEqualTo(2000);
        assertThat(saved.getProtein()).isEqualTo(110);
        assertThat(saved.getFat()).isEqualTo(50);
        assertThat(saved.getCarbohydrates()).isEqualTo(287);
        assertThat(profile.getCalories()).isEqualTo(1700);
    }

    @Test
    void setCustom_withoutCurrentGoal_usesRecommendedValuesForMissingFields() {
        UserProfile profile = profile(1700);
        UpdateGoalRequestDto request = new UpdateGoalRequestDto();
        request.setCalories(1800);
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(historyRepository.findFirstByUserIdAndEffectiveToIsNullOrderByEffectiveFromDesc(
                USER_ID)).thenReturn(Optional.empty());
        when(historyRepository.save(any(GoalHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GoalResponseDto result = service.setCustom(USER_ID, request);

        assertThat(result.getCalories()).isEqualTo(1800);
        assertThat(result.getProtein()).isEqualTo(90);
        assertThat(result.getFat()).isEqualTo(50);
        assertThat(result.getCarbohydrates()).isEqualTo(222);
        verify(historyRepository, never()).delete(any());
    }

    @Test
    void useRecommended_closesActiveCustomAndReturnsProfileGoal() {
        LocalDate today = LocalDate.now();
        UserProfile profile = profile(1700);
        GoalHistory current = history(2000, today.minusDays(2));
        when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
        when(historyRepository.findFirstByUserIdAndEffectiveToIsNullOrderByEffectiveFromDesc(
                USER_ID)).thenReturn(Optional.of(current));

        GoalResponseDto result = service.useRecommended(USER_ID);

        assertThat(result.getCalories()).isEqualTo(1700);
        assertThat(current.getEffectiveTo()).isEqualTo(today.minusDays(1));
        verify(historyRepository).save(current);
        verify(historyRepository, never()).delete(current);
    }

    private UserProfile profile(int calories) {
        UserProfile profile = new UserProfile();
        profile.setId(USER_ID);
        profile.setCalories(calories);
        profile.setProtein(90);
        profile.setFat(50);
        profile.setCarbohydrates(222);
        profile.setWaterGoalMl(2400);
        profile.setWaterGoalMode(WaterGoalMode.AUTO);
        return profile;
    }

    private GoalHistory history(int calories, LocalDate effectiveFrom) {
        return GoalHistory.builder().userId(USER_ID).calories(calories)
                .protein(200).fat(100).carbohydrates(825)
                .effectiveFrom(effectiveFrom).build();
    }
}
