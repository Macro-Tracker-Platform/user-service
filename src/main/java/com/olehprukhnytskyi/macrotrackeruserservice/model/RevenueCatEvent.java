package com.olehprukhnytskyi.macrotrackeruserservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "revenuecat_events")
public class RevenueCatEvent {
    @Id
    @Column(length = 255)
    private String id;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 255)
    private String appUserId;

    @Column(nullable = false)
    private Long eventTimestampMs;

    @Column(nullable = false)
    private Instant processedAt;
}
