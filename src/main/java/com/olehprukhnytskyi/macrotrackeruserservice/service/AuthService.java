package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.SocialTokenRequestDto;

public interface AuthService {
    String login(LoginRequestDto requestDto);

    String register(RegisterRequestDto requestDto);

    String authenticateWithSocial(SocialTokenRequestDto requestDto);
}
