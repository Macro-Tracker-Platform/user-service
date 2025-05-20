package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserProfileResponseDto;

public interface UserProfileService {
    UserProfileResponseDto findById(Long userId);
}
