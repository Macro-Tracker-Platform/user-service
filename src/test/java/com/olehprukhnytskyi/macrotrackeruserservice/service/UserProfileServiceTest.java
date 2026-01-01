package com.olehprukhnytskyi.macrotrackeruserservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.olehprukhnytskyi.exception.NotFoundException;
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
import com.olehprukhnytskyi.util.ActivityLevel;
import com.olehprukhnytskyi.util.BodyType;
import com.olehprukhnytskyi.util.Gender;
import com.olehprukhnytskyi.util.Goal;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {
    @Mock
    private UserProfileRepository userProfileRepository;
    @Mock
    private UserProfileMapper profileMapper;
    @Mock
    private GoalClient goalClient;

    @InjectMocks
    private UserProfileService userProfileService;

    @Test
    @DisplayName("When valid userId, should return user details")
    void findDetailsByUserId_whenValidUserId_shouldReturnUserDetails() {
        // Given
        Long userId = 1L;
        UserDetailsProjection projection = mock(UserDetailsProjection.class);
        UserDetailsResponseDto expectedDto = new UserDetailsResponseDto(25, 70, 180,
                Gender.MALE, ActivityLevel.MODERATELY_ACTIVE, Goal.MAINTAIN, BodyType.NORMAL);

        when(userProfileRepository.findDetailsByUserId(userId))
                .thenReturn(Optional.of(projection));
        when(profileMapper.toDto(projection)).thenReturn(expectedDto);

        // When
        UserDetailsResponseDto actual = userProfileService.findDetailsByUserId(userId);

        // Then
        assertThat(actual).isEqualTo(expectedDto);

        verify(userProfileRepository).findDetailsByUserId(userId);
        verify(profileMapper).toDto(projection);
    }

    @Test
    @DisplayName("When not existing userId, should throw an Exception")
    void findDetailsByUserId_whenNotExistingUserId_shouldThrowException() {
        // Given
        Long userId = 1L;

        when(userProfileRepository.findDetailsByUserId(userId))
                .thenReturn(Optional.empty());

        // When
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userProfileService.findDetailsByUserId(userId));

        // Then
        String expected = "Profile not found";
        assertEquals(expected, exception.getMessage());
    }

    @Test
    @DisplayName("When valid userId, should return user goal")
    void findGoalByUserId_whenValidUserId_shouldReturnUserGoal() {
        // Given
        Long userId = 1L;
        UserGoalProjection userGoalProjection = mock(UserGoalProjection.class);
        GoalResponseDto expected = GoalResponseDto.builder()
                .calories(1000)
                .carbohydrates(100)
                .fat(10)
                .protein(20)
                .build();

        when(userProfileRepository.findGoalsByUserId(userId))
                .thenReturn(Optional.of(userGoalProjection));
        when(profileMapper.toDto(userGoalProjection))
                .thenReturn(expected);

        // When
        GoalResponseDto actual = userProfileService.findGoalByUserId(userId);

        // Then
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("When not existing userId, should throw an Exception")
    void findGoalByUserId_whenNotExistingUserId_shouldThrowException() {
        // Given
        Long userId = 1L;

        when(userProfileRepository.findGoalsByUserId(userId))
                .thenReturn(Optional.empty());

        // When
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userProfileService.findGoalByUserId(userId));

        // Then
        String expected = "Profile not found";
        assertEquals(expected, exception.getMessage());
    }

    @Test
    @DisplayName("When not existing userId, should throw an Exception")
    void updateUserDetails_whenNotExistingUserId_shouldThrowException() {
        // Given
        Long userId = 1L;

        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.empty());

        // When
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userProfileService.updateUserDetails(
                        new UpdateUserDetailsRequestDto(), userId));

        // Then
        String expected = "Profile not found";
        assertEquals(expected, exception.getMessage());
    }

    @Test
    @DisplayName("When recalculate=true, should call goalClient and update profile")
    void updateUserDetails_whenRecalculateTrue_shouldUpdateGoalAndProfile() {
        // Given
        Long userId = 1L;
        UpdateUserDetailsRequestDto requestDto = UpdateUserDetailsRequestDto.builder()
                .age(25)
                .weight(70)
                .height(180)
                .gender(Gender.MALE)
                .activityLevel(ActivityLevel.EXTRA_ACTIVE)
                .goal(Goal.LOSE)
                .recalculate(true)
                .build();

        UserProfile profile = new UserProfile();
        GoalResponseDto calculatedGoal = new GoalResponseDto();
        UserDetailsResponseDto expectedResponse = new UserDetailsResponseDto();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(goalClient.calculateGoal(any())).thenReturn(calculatedGoal);
        when(profileMapper.toUserDetailsResponse(profile)).thenReturn(expectedResponse);

        // When
        UserDetailsResponseDto result = userProfileService.updateUserDetails(requestDto, userId);

        // Then
        verify(goalClient, times(1)).calculateGoal(any());
        verify(profileMapper, times(1)).updateUserGoalFromDto(profile, calculatedGoal);
        verify(profileMapper, times(1)).updateUserDetailsFromDto(profile, requestDto);
        verify(userProfileRepository, times(1)).save(profile);

        assertThat(result).isEqualTo(expectedResponse);
    }

    @Test
    @DisplayName("When recalculate=false, should only update details")
    void updateUserDetails_whenRecalculateFalse_shouldUpdateDetailsOnly() {
        // Given
        Long userId = 2L;
        UpdateUserDetailsRequestDto requestDto = UpdateUserDetailsRequestDto.builder()
                .age(30)
                .weight(80)
                .height(175)
                .gender(Gender.FEMALE)
                .activityLevel(ActivityLevel.LIGHTLY_ACTIVE)
                .goal(Goal.MAINTAIN)
                .recalculate(false)
                .build();

        UserProfile profile = new UserProfile();
        UserDetailsResponseDto expectedResponse = new UserDetailsResponseDto();

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileMapper.toUserDetailsResponse(profile)).thenReturn(expectedResponse);

        // When
        UserDetailsResponseDto result = userProfileService.updateUserDetails(requestDto, userId);

        // Then
        assertThat(result).isEqualTo(expectedResponse);

        verify(goalClient, never()).calculateGoal(any());
        verify(profileMapper, never()).updateUserGoalFromDto(any(), any());
        verify(profileMapper, times(1)).updateUserDetailsFromDto(profile, requestDto);
        verify(userProfileRepository, times(1)).save(profile);
    }

    @Test
    @DisplayName("When valid request, should update user goal and return response")
    void updateUserGoal_whenValidRequest_shouldUpdateGoal() {
        // Given
        Long userId = 1L;
        UpdateGoalRequestDto requestDto = new UpdateGoalRequestDto();
        requestDto.setCalories(1000);

        UserProfile profile = new UserProfile();
        profile.setId(userId);

        GoalResponseDto expectedResponse = new GoalResponseDto();
        expectedResponse.setCalories(1000);

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(profile));
        when(profileMapper.toUserGoalResponse(requestDto)).thenReturn(expectedResponse);
        when(profileMapper.toUserGoalResponse(profile)).thenReturn(expectedResponse);

        // When
        GoalResponseDto result = userProfileService.updateUserGoal(requestDto, userId);

        // Then
        assertThat(result).isEqualTo(expectedResponse);
        verify(profileMapper).updateUserGoalFromDto(eq(profile), eq(expectedResponse));
        verify(userProfileRepository).save(profile);
    }

    @Test
    @DisplayName("When not existing userId, should throw an Exception")
    void updateUserGoal_whenNotExistingUserId_shouldThrowException() {
        // Given
        Long userId = 1L;

        when(userProfileRepository.findById(userId))
                .thenReturn(Optional.empty());

        // When
        NotFoundException exception = assertThrows(NotFoundException.class,
                () -> userProfileService.updateUserGoal(new UpdateGoalRequestDto(), userId));

        // Then
        String expected = "Profile not found";
        assertEquals(expected, exception.getMessage());
    }
}
