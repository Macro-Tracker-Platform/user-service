package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import com.olehprukhnytskyi.event.RegistrationEvent;
import com.olehprukhnytskyi.exception.BadRequestException;
import com.olehprukhnytskyi.exception.InternalServerException;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.exception.error.CommonErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.client.GoalClient;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.AuthenticationException;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import jakarta.transaction.Transactional;
import java.text.ParseException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    private final SocialTokenVerificationService tokenVerificationService;
    private final UserProfileMapper userProfileMapper;
    private final OutboxRepository outboxRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final GoalClient goalClient;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode
                        .INVALID_CREDENTIALS, "Invalid email or password"));
        if (!user.isEmailConfirmed()) {
            throw new AuthenticationException(AuthErrorCode.EMAIL_NOT_CONFIRMED,
                    "Please confirm your email before logging in");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new AuthenticationException(AuthErrorCode.INVALID_CREDENTIALS,
                    "Invalid email or password");
        }
        return generateAuthResponse(user.getId(), user.getEmail());
    }

    public AuthResponseDto refreshToken(String refreshToken) {
        try {
            SignedJWT jwt = jwtUtil.parseAndValidate(refreshToken);
            Long userId = jwt.getJWTClaimsSet().getLongClaim("id");
            String email = jwt.getJWTClaimsSet().getSubject();
            return generateAuthResponse(userId, email);
        } catch (ParseException e) {
            throw new AuthenticationException(AuthErrorCode.INVALID_TOKEN,
                    "Invalid refresh token");
        }
    }

    @Transactional
    public void register(RegisterRequestDto registerDto) {
        if (userRepository.findByEmail(registerDto.getEmail()).isPresent()) {
            log.warn("Registration failed: email already exists");
            throw new AuthenticationException(AuthErrorCode.EMAIL_ALREADY_EXISTS,
                    "An account with this email already exists");
        }
        User user = userMapper.toUser(registerDto);
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));

        UserProfile profile = userMapper.toUserProfile(registerDto.getUserDetails(), user);
        GoalResponseDto goalResponseDto = goalClient.calculateGoal(registerDto.getUserDetails());
        userProfileMapper.updateUserProfileFromDto(goalResponseDto, profile);
        user.setProfile(profile);
        User savedUser = userRepository.save(user);

        String token = UUID.randomUUID().toString();
        savedUser.setEmailConfirmed(false);
        savedUser.setConfirmationToken(token);

        outboxRepository.save(generateUserRegisteredOutboxEvent(savedUser, token));
    }

    @Transactional
    public AuthResponseDto authenticateWithSocial(SocialTokenRequestDto tokenDto) {
        SocialUserDetails userDetails = tokenVerificationService.verifyToken(
                tokenDto.getToken(), tokenDto.getProvider());
        Optional<User> userFromDb = userRepository.findByEmail(userDetails.getEmail());
        if (userFromDb.isPresent()) {
            User user = userFromDb.get();
            if (!user.isEmailConfirmed()) {
                user.setEmailConfirmed(true);
                user.setConfirmationToken(null);
                userRepository.save(user);
            }
            return generateAuthResponse(user.getId(), user.getEmail());
        }
        User user = new User();
        user.setEmail(userDetails.getEmail());
        user.setAuthProvider(tokenDto.getProvider());
        user.setEmailConfirmed(true);
        user.setConfirmationToken(null);

        UserProfile profile = userMapper.toUserProfile(tokenDto.getUserDetails(), user);
        GoalResponseDto goalResponseDto = goalClient.calculateGoal(tokenDto.getUserDetails());
        userProfileMapper.updateUserProfileFromDto(goalResponseDto, profile);
        user.setProfile(profile);

        User savedUser = userRepository.save(user);
        return generateAuthResponse(savedUser.getId(), savedUser.getEmail());
    }

    public AuthResponseDto confirmEmail(String token) {
        User user = userRepository.findByConfirmationToken(token)
                .orElseThrow(() -> new InternalServerException(
                        AuthErrorCode.TOKEN_VERIFICATION_FAILED,
                        "Invalid token for email verification")
                );
        if (user.isEmailConfirmed()) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Email already confirmed");
        }
        user.setEmailConfirmed(true);
        user.setConfirmationToken(null);
        userRepository.save(user);
        return generateAuthResponse(user.getId(), user.getEmail());
    }

    @Transactional
    public void resendConfirmation(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode
                        .INVALID_CREDENTIALS, "User not found"));
        if (user.isEmailConfirmed()) {
            throw new BadRequestException(CommonErrorCode.BAD_REQUEST,
                    "Account is already confirmed");
        }
        String newToken = UUID.randomUUID().toString();
        user.setConfirmationToken(newToken);
        userRepository.save(user);
        outboxRepository.save(generateUserRegisteredOutboxEvent(user, newToken));
    }

    private AuthResponseDto generateAuthResponse(Long userId, String userEmail) {
        String access = jwtUtil.generateAccessToken(userId, userEmail);
        String refresh = jwtUtil.generateRefreshToken(userId, userEmail);
        return AuthResponseDto.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .build();
    }

    private OutboxEvent generateUserRegisteredOutboxEvent(User user, String token) {
        try {
            RegistrationEvent event = RegistrationEvent.builder()
                    .email(user.getEmail())
                    .confirmationToken(token)
                    .build();
            return OutboxEvent.builder()
                    .aggregateType("USER")
                    .aggregateId(user.getId().toString())
                    .eventType("USER_REGISTERED")
                    .payload(objectMapper.writeValueAsString(event))
                    .build();
        } catch (JsonProcessingException e) {
            throw new InternalServerException(CommonErrorCode.INTERNAL_ERROR,
                    "We couldn’t complete your registration. Please try again later", e);
        }
    }
}
