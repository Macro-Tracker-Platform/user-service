package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.client.GoalClient;
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
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.SocialTokenVerificationService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import jakarta.transaction.Transactional;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SocialTokenVerificationService tokenVerificationService;
    private final UserProfileMapper userProfileMapper;
    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final GoalClient goalClient;
    private final JwtUtil jwtUtil;

    @Override
    public String login(LoginRequestDto loginDto) {
        Optional<User> userFromDb = userRepository.findByEmail(loginDto.getEmail());
        if (userFromDb.isEmpty() || !BCrypt.checkpw(loginDto
                .getPassword(), userFromDb.get().getPassword())) {
            throw new AuthenticationException("Invalid email or password");
        }
        return jwtUtil.generateToken(userFromDb.get());
    }

    @Override
    @Transactional
    public String register(RegisterRequestDto registerDto) {
        if (userRepository.findByEmail(registerDto.getEmail()).isPresent()) {
            throw new AuthenticationException("An account with this email already exists");
        }
        User user = userMapper.toUser(registerDto);
        user.setPassword(BCrypt.hashpw(registerDto.getPassword(), BCrypt.gensalt()));
        return registerNewUser(user, registerDto.getUserDetails());
    }

    @Override
    @Transactional
    public String authenticateWithSocial(SocialTokenRequestDto tokenDto) {
        SocialUserDetails userDetails = tokenVerificationService.verifyToken(
                tokenDto.getToken(), tokenDto.getProvider());
        Optional<User> userFromDb = userRepository.findByEmail(userDetails.getEmail());
        if (userFromDb.isPresent()) {
            return jwtUtil.generateToken(userFromDb.get());
        }
        User user = new User();
        user.setEmail(userDetails.getEmail());
        user.setAuthProvider(tokenDto.getProvider());
        return registerNewUser(user, tokenDto.getUserDetails());
    }

    private String registerNewUser(User user, UserDetailsRequestDto userDetails) {
        UserProfile profile = userMapper.toUserProfile(userDetails, user);

        GoalResponseDto goalResponseDto = goalClient.calculateGoal(userDetails);
        userProfileMapper.updateUserProfileFromDto(goalResponseDto, profile);

        user.setProfile(profile);

        User savedUser = userRepository.save(user);
        return jwtUtil.generateToken(savedUser);
    }
}
