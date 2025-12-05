package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;

    @Transactional
    public void deleteById(Long userId) {
        userRepository.deleteById(userId);
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("USER")
                .aggregateId(userId.toString())
                .eventType("USER_DELETED")
                .build());
    }
}
