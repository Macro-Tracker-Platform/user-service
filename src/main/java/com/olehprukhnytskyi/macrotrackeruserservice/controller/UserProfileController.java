package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserProfileResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.CustomHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileService userProfileService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<UserProfileResponseDto> getProfile(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(userProfileService.findById(userId));
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteUser(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        userService.deleteById(userId);
        return ResponseEntity.noContent().build();
    }
}
