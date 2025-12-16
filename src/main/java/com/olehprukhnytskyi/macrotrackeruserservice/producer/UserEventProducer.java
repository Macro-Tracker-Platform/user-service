package com.olehprukhnytskyi.macrotrackeruserservice.producer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.EventProcessingException;
import com.olehprukhnytskyi.exception.error.EventErrorCode;
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
            kafkaTemplate.send("user-deleted", payload).get();
        } catch (JsonProcessingException e) {
            throw new EventProcessingException(EventErrorCode.KAFKA_SEND_FAILED,
                    "Cannot serialize event payload", e);
        } catch (Exception e) {
            throw new EventProcessingException(EventErrorCode.KAFKA_SEND_FAILED,
                    "Cannot send Kafka event", e);
        }
    }

    public void sendUserRegisteredEvent(String payload) {
        try {
            kafkaTemplate.send("user-registered", payload).get();
        } catch (Exception e) {
            throw new EventProcessingException(EventErrorCode.KAFKA_SEND_FAILED,
                    "Cannot send Kafka event", e);
        }
    }
}
