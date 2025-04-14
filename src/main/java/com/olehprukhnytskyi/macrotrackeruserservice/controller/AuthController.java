package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.ApiResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
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
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(
            @RequestBody @Valid LoginRequestDto requestDto) {
        String jwtToken = authService.login(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponseDto(jwtToken)));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDto>> register(
            @RequestBody @Valid RegisterRequestDto requestDto) {
        String jwtToken = authService.register(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponseDto(jwtToken)));
    }

    @PostMapping("/social")
    public ResponseEntity<ApiResponse<AuthResponseDto>> authenticateWithSocial(
            @RequestBody @Valid SocialTokenRequestDto requestDto) {
        String jwtToken = authService.authenticateWithSocial(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponseDto(jwtToken)));
    }
}
