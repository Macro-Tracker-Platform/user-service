package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
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
        private String store;

        @JsonProperty("product_id")
        private String productId;

        @JsonProperty("period_type")
        private String periodType;

        @JsonProperty("purchased_at_ms")
        private Long purchasedAtMs;

        @JsonProperty("transferred_from")
        private List<String> transferredFrom;

        @JsonProperty("transferred_to")
        private List<String> transferredTo;

        @JsonProperty("app_user_id")
        private String appUserId;

        @JsonProperty("event_timestamp_ms")
        private Long eventTimestampMs;
    }
}
