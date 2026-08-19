package com.olehprukhnytskyi.macrotrackeruserservice.model;

import com.olehprukhnytskyi.macrotrackeruserservice.util.SubscriptionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
@Table(name = "subscriptions")
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false)
    private String productId;

    private String basePlanId;

    @Column(nullable = false, columnDefinition = "text")
    private String purchaseTokenEncrypted;

    @Column(nullable = false, unique = true, length = 64)
    private String purchaseTokenHash;

    @Column(nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    private Instant startedAt;

    private Instant expiresAt;

    private boolean autoRenewing;

    private boolean acknowledged;

    private Instant lastVerifiedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
