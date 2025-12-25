package com.olehprukhnytskyi.macrotrackeruserservice.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.olehprukhnytskyi.event.PasswordResetEvent;
import com.olehprukhnytskyi.event.RegistrationEvent;
import com.olehprukhnytskyi.event.UserDeletedEvent;
import com.olehprukhnytskyi.macrotrackeruserservice.producer.UserEventProducer;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxJob {
    private final OutboxRepository outboxRepository;
    private final UserEventProducer userEventProducer;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(
            name = "processUserDeletedEvents",
            lockAtLeastFor = "PT2S",
            lockAtMostFor = "PT30S"
    )
    @Transactional
    public void processUserDeletedEvents() {
        List<OutboxEvent> events = outboxRepository
                .findTop100ByProcessedFalseAndEventTypeOrderByCreatedAtAsc("USER_DELETED");
        if (events.isEmpty()) {
            return;
        }

        List<OutboxEvent> processedEvents = new ArrayList<>();
        for (OutboxEvent event : events) {
            try {
                Long userId = Long.parseLong(event.getAggregateId());
                userEventProducer.sendUserDeletedEvent(new UserDeletedEvent(userId));

                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
                processedEvents.add(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
        if (!processedEvents.isEmpty()) {
            outboxRepository.saveAll(processedEvents);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(
            name = "processUserRegisteredEvents",
            lockAtLeastFor = "PT2S",
            lockAtMostFor = "PT30S"
    )
    @Transactional
    public void processUserRegisteredEvents() {
        List<OutboxEvent> events = outboxRepository
                .findTop100ByProcessedFalseAndEventTypeOrderByCreatedAtAsc("USER_REGISTERED");
        if (events.isEmpty()) {
            return;
        }

        List<OutboxEvent> processedEvents = new ArrayList<>();
        for (OutboxEvent event : events) {
            try {
                String payload = event.getPayload();
                RegistrationEvent registrationEvent = objectMapper
                        .readValue(payload, RegistrationEvent.class);
                userEventProducer.sendUserRegisteredEvent(registrationEvent);

                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
                processedEvents.add(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
        if (!processedEvents.isEmpty()) {
            outboxRepository.saveAll(processedEvents);
        }
    }

    @Scheduled(fixedDelay = 5000)
    @SchedulerLock(
            name = "processPasswordResetEvents",
            lockAtLeastFor = "PT2S",
            lockAtMostFor = "PT30S"
    )
    @Transactional
    public void processPasswordResetEvents() {
        List<OutboxEvent> events = outboxRepository
                .findTop100ByProcessedFalseAndEventTypeOrderByCreatedAtAsc(
                        "PASSWORD_RESET_REQUESTED");
        if (events.isEmpty()) {
            return;
        }

        List<OutboxEvent> processedEvents = new ArrayList<>();
        for (OutboxEvent event : events) {
            try {
                String payload = event.getPayload();
                PasswordResetEvent passwordResetEvent = objectMapper
                        .readValue(payload, PasswordResetEvent.class);
                userEventProducer.sendPasswordResetEvent(passwordResetEvent);

                event.setProcessed(true);
                event.setProcessedAt(Instant.now());
                processedEvents.add(event);
            } catch (Exception e) {
                log.error("Failed to process outbox event {}: {}", event.getId(), e.getMessage());
            }
        }
        if (!processedEvents.isEmpty()) {
            outboxRepository.saveAll(processedEvents);
        }
    }
}
