package com.olehprukhnytskyi.macrotrackeruserservice.dto;

import java.time.Instant;

public record WebCommissionResponseDto(String invoiceId, String checkoutId, String plan,
                                       String currency, long amountPaid, long refundedAmount,
                                       Long affiliateId, String affiliateName,
                                       long affiliateCommission, Long referrerId,
                                       String referrerName, long referrerCommission,
                                       Instant paidAt) {
}
