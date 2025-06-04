package com.olehprukhnytskyi.macrotrackeruserservice.mapper;

import com.olehprukhnytskyi.macrotrackeruserservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.GoalResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UpdateUserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsResponseDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserDetailsProjection;
import com.olehprukhnytskyi.macrotrackeruserservice.projection.UserGoalProjection;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapperConfig.class,
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserProfileMapper {
    void updateUserProfileFromDto(GoalResponseDto dto, @MappingTarget UserProfile profile);

    GoalResponseDto toDto(UserGoalProjection projection);

    UserDetailsResponseDto toDto(UserDetailsProjection projection);

    void updateUserDetailsFromDto(@MappingTarget UserProfile entity,
                                  UpdateUserDetailsRequestDto dto);

    UserDetailsResponseDto toUserDetailsResponse(UserProfile entity);
}
