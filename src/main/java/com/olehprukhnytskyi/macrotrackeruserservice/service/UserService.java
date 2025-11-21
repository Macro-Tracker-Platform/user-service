package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.model.OutboxEvent;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.OutboxRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
