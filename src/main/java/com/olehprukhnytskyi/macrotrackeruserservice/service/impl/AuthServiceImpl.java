package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.AuthenticationException;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.SocialTokenVerificationService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SocialTokenVerificationService tokenVerificationService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Override
    public String login(LoginRequestDto requestDto) {
        Optional<User> user = userRepository.findByEmail(requestDto.getEmail());
        if (user.isEmpty() || !BCrypt.checkpw(requestDto
                .getPassword(), user.get().getPassword())) {
            throw new AuthenticationException("Invalid email or password");
        }
        return jwtUtil.generateJwtToken(user.get());
    }

    @Override
    public String register(RegisterRequestDto requestDto) {
        Optional<User> userFromDatabase = userRepository.findByEmail(requestDto.getEmail());
        if (userFromDatabase.isPresent()) {
            throw new IllegalStateException("An account with this email already exists");
        }
        String hashedPassword = BCrypt.hashpw(requestDto.getPassword(), BCrypt.gensalt());
        User user = new User();
        user.setEmail(requestDto.getEmail());
        user.setPassword(hashedPassword);
        return jwtUtil.generateJwtToken(userRepository.save(user));
    }

    @Override
    public String authenticateWithSocial(String provider, String token) {
        SocialUserPayload payload = tokenVerificationService.verifyToken(token, provider);
        User user = userRepository.findByEmail(payload.getEmail())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(payload.getEmail());
                    newUser.setAuthProvider(provider);
                    return userRepository.save(newUser);
                });
        return jwtUtil.generateJwtToken(user);
    }
}
