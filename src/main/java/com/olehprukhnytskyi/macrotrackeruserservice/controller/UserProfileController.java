package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.CustomHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileService userProfileService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserDetailsResponseDto> getUserDetails(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(userProfileService.findDetailsByUserId(userId));
    }

    @GetMapping("/goal")
    public ResponseEntity<GoalResponseDto> getUserGoal(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(userProfileService.findGoalByUserId(userId));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        userService.deleteById(userId);
        return ResponseEntity.noContent().build();
    }
}
