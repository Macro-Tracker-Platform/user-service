package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.producer.UserEventProducer;
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
    private final UserEventProducer userEventProducer;

    @Transactional
    @Override
    public void deleteById(Long userId) {
        try {
            userRepository.deleteById(userId);
            userEventProducer.sendUserDeletedEvent(userId);
        } catch (Exception e) {
            log.error("Failed to delete user with id={}", userId, e);
            throw e;
        }
    }
}
