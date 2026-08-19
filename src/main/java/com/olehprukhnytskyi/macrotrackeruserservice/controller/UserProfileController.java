package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.DatedGoalDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalChangeDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalScheduleDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateUserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateWaterGoalRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.GoalScheduleService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserService;
import com.olehprukhnytskyi.util.CustomHeaders;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(
        name = "Profile API",
        description = "User profile and goal management endpoints"
)
public class UserProfileController {
    private final UserProfileService userProfileService;
    private final UserService userService;
    private final GoalScheduleService goalScheduleService;

    @Operation(
            summary = "Get user details",
            description = "Retrieve user profile information (age, weight, height, etc.)"
    )
    @GetMapping("/details")
    public ResponseEntity<UserDetailsResponseDto> getUserDetails(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Fetching user details for userId={}", userId);
        UserDetailsResponseDto details = userProfileService.findDetailsByUserId(userId);
        log.info("Fetched user details for userId={}", userId);
        return ResponseEntity.ok(details);
    }

    @Operation(
            summary = "Get user goals",
            description = "Retrieve user nutrition and water goals"
    )
    @GetMapping("/goal")
    public ResponseEntity<GoalResponseDto> getUserGoal(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam(required = false) LocalDate date) {
        log.info("Fetching user goals for userId={}", userId);
        GoalResponseDto goal = date == null ? userProfileService.findGoalByUserId(userId)
                : goalScheduleService.resolve(userId, date);
        log.info("Fetched user goals for userId={}", userId);
        return ResponseEntity.ok(goal);
    }

    @Operation(
            summary = "Delete user account",
            description = "Permanently delete user account and all associated data"
    )
    @DeleteMapping
    public ResponseEntity<Void> deleteUser(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Deleting user account for userId={}", userId);
        userService.deleteById(userId);
        log.info("Deleted user account for userId={}", userId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Update user details",
            description = "Update user profile information"
    )
    @PatchMapping("/details")
    public ResponseEntity<UserDetailsResponseDto> updateUserDetails(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestBody @Valid UpdateUserDetailsRequestDto requestDto) {
        log.info("Updating user details for userId={}", userId);
        UserDetailsResponseDto details = userProfileService
                .updateUserDetails(requestDto, userId);
        log.info("Updated user details for userId={}", userId);
        return ResponseEntity.ok(details);
    }

    @Operation(
            summary = "Update nutrition goals",
            description = "Update user daily nutrition targets"
    )
    @PatchMapping("/goal")
    public ResponseEntity<GoalResponseDto> updateGoal(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestBody(required = false) @Valid UpdateGoalRequestDto requestDto) {
        log.info("Updating nutrition goals for userId={}", userId);
        GoalResponseDto updatedGoal = userProfileService.updateUserGoal(requestDto, userId);
        log.info("Updated nutrition goals for userId={}", userId);
        return ResponseEntity.ok(updatedGoal);
    }

    @GetMapping("/goal/schedules")
    public ResponseEntity<List<GoalScheduleDto>> getGoalSchedules(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(goalScheduleService.getActive(userId));
    }

    @GetMapping("/goal/last-change")
    public ResponseEntity<GoalChangeDto> getLastGoalChange(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(new GoalChangeDto(
                goalScheduleService.lastGoalChange(userId).orElse(null)));
    }

    @GetMapping("/goal/range")
    public ResponseEntity<List<DatedGoalDto>> getGoalsByDateRange(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestParam LocalDate from,
            @RequestParam LocalDate to) {
        if (to.isBefore(from) || to.isAfter(from.plusDays(90))) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(from.datesUntil(to.plusDays(1))
                .map(date -> new DatedGoalDto(date, goalScheduleService.resolve(userId, date)))
                .toList());
    }

    @PutMapping("/goal/schedules/{dayOfWeek}")
    public ResponseEntity<GoalScheduleDto> putGoalSchedule(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable DayOfWeek dayOfWeek,
            @RequestBody @Valid GoalScheduleDto request) {
        request.setDayOfWeek(dayOfWeek);
        return ResponseEntity.ok(goalScheduleService.put(userId, dayOfWeek, request));
    }

    @DeleteMapping("/goal/schedules/{dayOfWeek}")
    public ResponseEntity<Void> deleteGoalSchedule(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @PathVariable DayOfWeek dayOfWeek) {
        goalScheduleService.delete(userId, dayOfWeek);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "Set custom water goal",
            description = "Set a custom daily water target that profile recalculation preserves"
    )
    @PutMapping("/goal/water")
    public ResponseEntity<GoalResponseDto> updateWaterGoal(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId,
            @RequestBody @Valid UpdateWaterGoalRequestDto requestDto) {
        log.info("Updating custom water goal for userId={}", userId);
        return ResponseEntity.ok(userProfileService.updateWaterGoal(requestDto, userId));
    }

    @Operation(
            summary = "Reset water goal",
            description = "Return to an automatically calculated daily water target"
    )
    @DeleteMapping("/goal/water")
    public ResponseEntity<GoalResponseDto> resetWaterGoal(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        log.info("Resetting water goal for userId={}", userId);
        return ResponseEntity.ok(userProfileService.resetWaterGoal(userId));
    }
}
