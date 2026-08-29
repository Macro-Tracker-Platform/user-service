package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RevenueCatWebhookDto {
    @JsonProperty("api_version")
    private String apiVersion;

    private Event event;

    @Getter
    @NoArgsConstructor
    public static class Event {
        private String id;
        private String type;

        @JsonProperty("app_user_id")
        private String appUserId;

        @JsonProperty("event_timestamp_ms")
        private Long eventTimestampMs;
    }
}
