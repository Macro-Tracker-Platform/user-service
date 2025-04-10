package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialUserPayload;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.AuthService;
import com.olehprukhnytskyi.macrotrackeruserservice.service.SocialTokenVerificationService;
import com.olehprukhnytskyi.macrotrackeruserservice.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final SocialTokenVerificationService tokenVerificationService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

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
