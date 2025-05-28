package com.olehprukhnytskyi.macrotrackeruserservice.service.impl;

import com.olehprukhnytskyi.macrotrackeruserservice.producer.UserEventProducer;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserEventProducer userEventProducer;

    @Transactional
    @Override
    public void deleteById(Long userId) {
        userRepository.deleteById(userId);
        userEventProducer.sendUserDeletedEvent(userId);
    }
}
