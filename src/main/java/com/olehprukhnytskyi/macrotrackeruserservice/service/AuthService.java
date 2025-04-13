package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.LoginRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;

public interface AuthService {
    String login(LoginRequestDto requestDto);

    String register(RegisterRequestDto requestDto);

    String authenticateWithSocial(String provider, String token);
}
