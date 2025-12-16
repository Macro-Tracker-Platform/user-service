package com.olehprukhnytskyi.macrotrackeruserservice.job;

import com.olehprukhnytskyi.macrotrackeruserservice.producer.UserEventProducer;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxJob {
    private final OutboxRepository outboxRepository;
    private final UserEventProducer userEventProducer;

    @Scheduled(fixedDelay = 5000)
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
                userEventProducer.sendUserDeletedEvent(userId);

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
                userEventProducer.sendUserRegisteredEvent(payload);

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
