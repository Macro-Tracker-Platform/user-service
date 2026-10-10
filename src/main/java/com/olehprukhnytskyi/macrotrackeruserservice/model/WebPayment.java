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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "web_payments")
public class WebPayment {
    @Id
    private String id;

    @Column(nullable = false, length = 36)
    private String checkoutId;

    @Column(nullable = false)
    private long amountPaid;

    @Column(nullable = false)
    private long commissionBase;

    @Column(nullable = false)
    private Instant periodStart;

    @Column(nullable = false)
    private boolean recurringPeriod;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant paidAt;

    @Column(nullable = false)
    private long refundedAmount;

    @Column(nullable = false)
    private long commissionAmount;

    @Column(nullable = false)
    private long referrerCommissionAmount;
}
