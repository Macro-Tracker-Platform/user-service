package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import lombok.Data;

@Data
public class GoogleRtdnRequestDto {
    private PubSubMessage message;
    private String subscription;

    @Data
    public static class PubSubMessage {
        private String messageId;
        private String data;
    }
}
