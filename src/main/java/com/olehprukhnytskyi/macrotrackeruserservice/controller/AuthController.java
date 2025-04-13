package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.ApiResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequest;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import jakarta.validation.Valid;
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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid LoginRequestDto requestDto) {
        String jwtToken = authService.login(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(jwtToken)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @RequestBody @Valid RegisterRequestDto requestDto) {
        String jwtToken = authService.register(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(jwtToken)));
    }

    @PostMapping("/social")
    public ResponseEntity<ApiResponse<AuthResponse>> authenticateWithSocial(
            @RequestBody @Valid SocialTokenRequest request) {
        String jwtToken = authService.authenticateWithSocial(
                request.getProvider(), request.getToken());
        return ResponseEntity.ok(ApiResponse.success(new AuthResponse(jwtToken)));
    }
}
