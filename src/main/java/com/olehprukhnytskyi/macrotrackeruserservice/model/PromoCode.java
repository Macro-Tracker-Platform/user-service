package com.olehprukhnytskyi.macrotrackeruserservice.model;

import com.olehprukhnytskyi.macrotrackeruserservice.util.PromoAcquisitionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "promo_codes")
public class PromoCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    private String partnerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private PromoAcquisitionType acquisitionType = PromoAcquisitionType.DIRECT;

    @ManyToOne
    @JoinColumn(name = "acquisition_manager_id")
    private AcquisitionManager acquisitionManager;

    @ManyToOne
    @JoinColumn(name = "web_affiliate_id")
    private WebAffiliate webAffiliate;

    @Column(nullable = false)
    private Integer discountPercent;

    private String monthlyOfferId;

    private String yearlyOfferId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    private Instant validFrom;

    private Instant validUntil;

    private Integer maxRedemptions;

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
