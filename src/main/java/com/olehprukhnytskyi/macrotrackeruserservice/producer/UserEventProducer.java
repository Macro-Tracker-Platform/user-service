package com.olehprukhnytskyi.macrotrackeruserservice.producer;

import com.olehprukhnytskyi.event.PasswordResetEvent;
import com.olehprukhnytskyi.event.RegistrationEvent;
import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.exception.EventProcessingException;
import com.olehprukhnytskyi.exception.error.EventErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserEventProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendUserDeletedEvent(UserDeletedEvent event) {
        try {
            kafkaTemplate.send("user-deleted", event).get();
        } catch (Exception e) {
            throw new EventProcessingException(EventErrorCode.KAFKA_SEND_FAILED,
                    "Cannot send Kafka event", e);
        }
    }

    public void sendUserRegisteredEvent(RegistrationEvent event) {
        try {
            kafkaTemplate.send("user-registered", event).get();
        } catch (Exception e) {
            throw new EventProcessingException(EventErrorCode.KAFKA_SEND_FAILED,
                    "Cannot send Kafka event", e);
        }
    }

    public void sendPasswordResetEvent(PasswordResetEvent event) {
        try {
            kafkaTemplate.send("user-password-reset", event).get();
        } catch (Exception e) {
            throw new EventProcessingException(EventErrorCode.KAFKA_SEND_FAILED,
                    "Cannot send Kafka event", e);
        }
    }
}
