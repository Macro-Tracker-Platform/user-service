package com.olehprukhnytskyi.macrotrackeruserservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "user_entitlements")
public class UserEntitlement {
    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "is_subscribed", nullable = false)
    private boolean subscribed;

    @Column(name = "subscription_event_timestamp_ms", nullable = false)
    private Long subscriptionEventTimestampMs;
}
