package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.model.OutboxEvent;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.OutboxRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;

    @Transactional
    @Override
    public void deleteById(Long userId) {
        try {
            userRepository.deleteById(userId);
            outboxRepository.save(OutboxEvent.builder()
                    .aggregateType("USER")
                    .aggregateId(userId.toString())
                    .eventType("USER_DELETED")
                    .build());
        } catch (Exception e) {
            log.error("Failed to delete user with id={}", userId, e);
            throw e;
        }
    }
}
