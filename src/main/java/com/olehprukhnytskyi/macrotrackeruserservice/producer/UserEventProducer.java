package com.olehprukhnytskyi.macrotrackeruserservice.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.macrotrackeruserservice.event.UserDeletedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserEventProducer {
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendUserDeletedEvent(Long userId) {
        try {
            UserDeletedEvent event = new UserDeletedEvent(userId);
            String payload = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("user-deleted", payload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Cannot send kafka event", e);
        }
    }
}
