package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserDetails;
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
        Optional<User> userFromDb = userRepository.findByEmail(requestDto.getEmail());
        if (userFromDb.isEmpty() || !BCrypt.checkpw(requestDto
                .getPassword(), userFromDb.get().getPassword())) {
            throw new AuthenticationException("Invalid email or password");
        }
        return jwtUtil.generateToken(userFromDb.get());
    }

    @Override
    public String register(RegisterRequestDto requestDto) {
        Optional<User> userFromDb = userRepository.findByEmail(requestDto.getEmail());
        if (userFromDb.isPresent()) {
            throw new AuthenticationException("An account with this email already exists");
        }
        String hashedPassword = BCrypt.hashpw(requestDto.getPassword(), BCrypt.gensalt());
        User user = new User();
        user.setEmail(requestDto.getEmail());
        user.setPassword(hashedPassword);
        user.setAuthProvider("local");
        return jwtUtil.generateToken(userRepository.save(user));
    }

    @Override
    public String authenticateWithSocial(SocialTokenRequestDto requestDto) {
        SocialUserDetails userDetails = tokenVerificationService.verifyToken(
                requestDto.getToken(), requestDto.getProvider());
        User userFromDb = userRepository.findByEmail(userDetails.getEmail())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setEmail(userDetails.getEmail());
                    newUser.setAuthProvider(requestDto.getProvider());
                    return userRepository.save(newUser);
                });
        return jwtUtil.generateToken(userFromDb);
    }
}
