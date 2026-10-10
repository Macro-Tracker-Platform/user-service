package com.olehprukhnytskyi.macrotrackeruserservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackeruserservice.model.WebPayment;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebPaymentRepository extends JpaRepository<WebPayment, String> {
    List<WebPayment> findByCheckoutIdOrderByPaidAtAscIdAsc(String checkoutId);
}
