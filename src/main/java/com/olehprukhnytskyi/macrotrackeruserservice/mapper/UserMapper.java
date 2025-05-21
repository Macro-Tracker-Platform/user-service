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
    @Mapping(target = "authProvider", constant = "LOCAL")
    User toUser(RegisterRequestDto dto);

    @Mapping(target = "user", source = "user")
    UserProfile toUserProfile(UserDetailsRequestDto dto, User user);
}
