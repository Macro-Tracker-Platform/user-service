package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPaymentRefund;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebPaymentRefundRepository extends JpaRepository<WebPaymentRefund, String> {
    List<WebPaymentRefund> findByInvoiceId(String invoiceId);
}
