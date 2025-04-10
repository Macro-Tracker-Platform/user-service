package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.ApiResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequest;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    @PostMapping("/social")
    public ResponseEntity<ApiResponse<AuthResponse>> authenticateWithSocial(
            @RequestBody SocialTokenRequest request) {
        String jwtToken = authService.authenticateWithSocial(
                request.getProvider(), request.getToken());
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(jwtToken)));
    }
}
