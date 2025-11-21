package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Facebook user profile response")
public class FacebookUserResponseDto {
    @Schema(description = "User email from Facebook", example = "user@example.com")
    private String email;
}
