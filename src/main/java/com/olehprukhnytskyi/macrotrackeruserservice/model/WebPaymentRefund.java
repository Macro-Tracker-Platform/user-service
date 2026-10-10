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
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "web_payment_refunds")
public class WebPaymentRefund {
    @Id
    private String id;

    @Column(nullable = false)
    private String invoiceId;

    @Column(nullable = false)
    private long amount;
}
