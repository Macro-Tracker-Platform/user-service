package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.ApiResponse;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(
        name = "Authentication API",
        description = "User authentication and registration endpoints"
)
public class AuthController {
    private final AuthService authService;

    @Operation(
            summary = "User login",
            description = "Authenticate user with email and password"
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponseDto>> login(
            @RequestBody @Valid LoginRequestDto requestDto) {
        String token = authService.login(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponseDto(token)));
    }

    @Operation(
            summary = "User registration",
            description = "Create new user account with profile details"
    )
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponseDto>> register(
            @RequestBody @Valid RegisterRequestDto requestDto) {
        String token = authService.register(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponseDto(token)));
    }

    @Operation(
            summary = "Social authentication",
            description = "Authenticate using social providers (Google, Facebook, etc.)"
    )
    @PostMapping("/social")
    public ResponseEntity<ApiResponse<AuthResponseDto>> authenticateWithSocial(
            @RequestBody @Valid SocialTokenRequestDto requestDto) {
        String token = authService.authenticateWithSocial(requestDto);
        return ResponseEntity.ok(ApiResponse.success(new AuthResponseDto(token)));
    }
}
