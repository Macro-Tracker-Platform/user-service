package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FacebookTokenDebugResponse {
    private Data data;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Data {
        @JsonProperty("is_valid")
        private boolean isValid;
        @JsonProperty("app_id")
        private String appId;
    }
}
