package com.olehprukhnytskyi.macrotrackeruserservice.repository;

import com.olehprukhnytskyi.macrotrackeruserservice.model.OutboxEvent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {
    List<OutboxEvent> findTop100ByProcessedFalseAndEventTypeOrderByCreatedAtAsc(String eventType);
}
