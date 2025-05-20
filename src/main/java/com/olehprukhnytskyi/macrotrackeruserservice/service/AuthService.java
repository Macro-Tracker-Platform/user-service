package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;

public interface AuthService {
    String login(LoginRequestDto loginDto);

    String register(RegisterRequestDto registerDto);

    String authenticateWithSocial(SocialTokenRequestDto tokenDto);
}
