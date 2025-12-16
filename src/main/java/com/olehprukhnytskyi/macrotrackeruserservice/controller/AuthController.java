package com.olehprukhnytskyi.macrotrackeruserservice.controller;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RefreshRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
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
    public ResponseEntity<AuthResponseDto> login(
            @RequestBody @Valid LoginRequestDto requestDto) {
        log.info("Login request received");
        AuthResponseDto tokens = authService.login(requestDto);
        log.info("Login successful");
        return ResponseEntity.ok(tokens);
    }

    @Operation(
            summary = "Refresh access token",
            description = "Generate a new pair of access and"
                    + " refresh tokens using a valid refresh token"
    )
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponseDto> refresh(@RequestBody @Valid RefreshRequestDto dto) {
        log.info("Refresh token request");
        AuthResponseDto tokens = authService.refreshToken(dto.getRefreshToken());
        return ResponseEntity.ok(tokens);
    }

    @Operation(
            summary = "User registration",
            description = "Create new user account with profile details"
    )
    @PostMapping("/register")
    public ResponseEntity<Void> register(
            @RequestBody @Valid RegisterRequestDto requestDto) {
        log.info("Registration request received");
        authService.register(requestDto);
        log.info("Registration successful");
        return ResponseEntity.ok().build();
    }

    @Operation(
            summary = "Social authentication",
            description = "Authenticate using social providers (Google, Facebook, etc.)"
    )
    @PostMapping("/social")
    public ResponseEntity<AuthResponseDto> authenticateWithSocial(
            @RequestBody @Valid SocialTokenRequestDto requestDto) {
        log.info("Social authentication request received");
        AuthResponseDto tokens = authService.authenticateWithSocial(requestDto);
        log.info("Social authentication successful");
        return ResponseEntity.ok(tokens);
    }

    @Operation(
            summary = "Confirm email address",
            description = "Confirms the user's email address using"
                    + " a confirmation token sent by email during registration"
    )
    @GetMapping("/confirm")
    public ResponseEntity<AuthResponseDto> confirm(@RequestParam String token) {
        log.info("Confirm email request received");
        AuthResponseDto response = authService.confirmEmail(token);
        log.info("Confirm email successful");
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Resend email confirmation",
            description = "Generates a new email confirmation token and sends it to"
                    + " the user's email address if the account is not yet confirmed"
    )
    @PostMapping("/resend")
    public ResponseEntity<String> resend(@RequestParam String email) {
        log.info("Resend email confirmation request received");
        authService.resendConfirmation(email);
        log.info("Resend email confirmation successful");
        return ResponseEntity.ok("Confirmation email resent");
    }
}
