package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.nimbusds.jwt.SignedJWT;
import com.olehprukhnytskyi.exception.error.AuthErrorCode;
import com.olehprukhnytskyi.macrotrackeruserservice.client.GoalClient;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.AuthResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.AuthenticationException;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import jakarta.transaction.Transactional;
import java.text.ParseException;
import java.util.Optional;
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
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final GoalClient goalClient;
    private final JwtUtil jwtUtil;

    public AuthResponseDto login(LoginRequestDto dto) {
        User user = userRepository.findByEmail(dto.getEmail())
                .orElseThrow(() -> new AuthenticationException(AuthErrorCode
                        .INVALID_CREDENTIALS, "Invalid email or password"));
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
    public AuthResponseDto register(RegisterRequestDto registerDto) {
        if (userRepository.findByEmail(registerDto.getEmail()).isPresent()) {
            log.warn("Registration failed: email already exists");
            throw new AuthenticationException(AuthErrorCode.EMAIL_ALREADY_EXISTS,
                    "An account with this email already exists");
        }
        User user = userMapper.toUser(registerDto);
        user.setPassword(passwordEncoder.encode(registerDto.getPassword()));
        return registerNewUser(user, registerDto.getUserDetails());
    }

    @Transactional
    public AuthResponseDto authenticateWithSocial(SocialTokenRequestDto tokenDto) {
        SocialUserDetails userDetails = tokenVerificationService.verifyToken(
                tokenDto.getToken(), tokenDto.getProvider());
        Optional<User> userFromDb = userRepository.findByEmail(userDetails.getEmail());
        if (userFromDb.isPresent()) {
            return generateAuthResponse(userFromDb.get().getId(), userFromDb.get().getEmail());
        }
        User user = new User();
        user.setEmail(userDetails.getEmail());
        user.setAuthProvider(tokenDto.getProvider());
        return registerNewUser(user, tokenDto.getUserDetails());
    }

    private AuthResponseDto generateAuthResponse(Long userId, String userEmail) {
        String access = jwtUtil.generateAccessToken(userId, userEmail);
        String refresh = jwtUtil.generateRefreshToken(userId, userEmail);
        return AuthResponseDto.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .build();
    }

    private AuthResponseDto registerNewUser(User user, UserDetailsRequestDto userDetails) {
        UserProfile profile = userMapper.toUserProfile(userDetails, user);

        GoalResponseDto goalResponseDto = goalClient.calculateGoal(userDetails);
        userProfileMapper.updateUserProfileFromDto(goalResponseDto, profile);

        user.setProfile(profile);

        User savedUser = userRepository.save(user);
        return generateAuthResponse(savedUser.getId(), savedUser.getEmail());
    }
}
