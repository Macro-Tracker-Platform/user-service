package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Facebook token validation response")
public class FacebookTokenDebugResponseDto {
    @Schema(description = "Token validation data")
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    @Schema(description = "Facebook token validation data")
    public static class Data {
        @Schema(description = "Indicates if the token is valid", example = "true")
        @JsonProperty("is_valid")
        private boolean isValid;

        @Schema(description = "Facebook application ID", example = "123456789012345")
        @JsonProperty("app_id")
        private String appId;
    }
}
