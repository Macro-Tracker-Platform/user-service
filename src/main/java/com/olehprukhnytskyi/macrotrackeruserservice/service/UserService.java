package com.olehprukhnytskyi.macrotrackeruserservice.service;

import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.UserRepository;
import com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa.WebCheckoutRepository;
import com.olehprukhnytskyi.model.OutboxEvent;
import com.olehprukhnytskyi.repository.jpa.OutboxRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final OutboxRepository outboxRepository;
    private final WebCheckoutRepository checkouts;
    private final WebAccountDeletionService billingDeletion;

    @Transactional
    public void deleteById(Long userId) {
        if (userRepository.findByIdForUpdate(userId).isEmpty()) {
            // A retry after a lost successful response must still let the client log out.
            return;
        }
        try {
            billingDeletion.prepare(userId);
        } catch (ResponseStatusException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "ACCOUNT_DELETION_BILLING_UNCONFIRMED", exception);
        }
        for (var found : checkouts.findByUserIdOrderByIdAsc(userId)) {
            var checkout = checkouts.findByIdForUpdate(found.getId()).orElseThrow();
            checkout.setAccountDeletedAt(Instant.now());
            checkout.setCheckoutUrl(null);
        }
        userRepository.deleteById(userId);
        outboxRepository.save(OutboxEvent.builder()
                .aggregateType("USER")
                .aggregateId(userId.toString())
                .eventType("USER_DELETED")
                .build());
    }
}
