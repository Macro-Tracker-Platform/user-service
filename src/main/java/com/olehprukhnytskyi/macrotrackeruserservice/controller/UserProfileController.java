package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserProfileResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.CustomHeaders;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class UserProfileController {
    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponseDto> getProfile(
            @RequestHeader(CustomHeaders.X_USER_ID) Long userId) {
        return ResponseEntity.ok(userProfileService.findById(userId));
    }
}
