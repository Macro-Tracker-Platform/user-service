package com.olehprukhnytskyi.macrotrackeruserservice.mapper;

import com.olehprukhnytskyi.macrotrackeruserservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserProfileResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapperConfig.class,
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface UserProfileMapper {
    void updateUserProfileFromDto(GoalResponseDto dto, @MappingTarget UserProfile profile);

    UserProfileResponseDto toDto(UserProfile userProfile);
}
