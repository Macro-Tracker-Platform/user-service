package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserProfileResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.exception.NotFoundException;
import com.olehprukhnytskyi.macrotrackeruserservice.mapper.UserProfileMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserProfileRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private final UserProfileRepository repository;
    private final UserProfileMapper mapper;

    @Override
    public UserProfileResponseDto findById(Long userId) {
        UserProfile profile = repository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Profile not found"));
        return mapper.toDto(profile);
    }
}
