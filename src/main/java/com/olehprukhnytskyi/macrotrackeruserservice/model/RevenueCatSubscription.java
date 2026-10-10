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
import lombok.Setter;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "revenuecat_subscriptions")
public class RevenueCatSubscription {
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String store;

    @Column(nullable = false)
    private String originalTransactionId;

    @Column(nullable = false)
    private String productId;

    @Column(length = 32)
    private String periodType;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Long eventTimestampMs;
}
