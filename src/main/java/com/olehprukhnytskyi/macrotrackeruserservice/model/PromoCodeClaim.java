package com.olehprukhnytskyi.macrotrackeruserservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "promo_code_claims")
public class PromoCodeClaim {
    @Id
    private Long userId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "promo_code_id", nullable = false)
    private PromoCode promoCode;

    @Column(nullable = false)
    private Instant claimedAt;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant consumedAt;

    @ManyToOne
    @JoinColumn(name = "subscription_id")
    private Subscription subscription;
}
