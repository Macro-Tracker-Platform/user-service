package com.olehprukhnytskyi.macrotrackeruserservice.mapper;

import com.olehprukhnytskyi.macrotrackeruserservice.config.MapperConfig;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.RegisterRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.dto.UserDetailsRequestDto;
import com.olehprukhnytskyi.macrotrackeruserservice.model.User;
import com.olehprukhnytskyi.macrotrackeruserservice.model.UserProfile;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(config = MapperConfig.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {
    User toUser(RegisterRequestDto dto);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "calories", ignore = true)
    @Mapping(target = "carbohydrates", ignore = true)
    @Mapping(target = "fat", ignore = true)
    @Mapping(target = "protein", ignore = true)
    @Mapping(target = "waterGoalMl", ignore = true)
    @Mapping(target = "waterGoalMode", ignore = true)
    UserProfile toUserProfile(UserDetailsRequestDto dto, User user);
}
