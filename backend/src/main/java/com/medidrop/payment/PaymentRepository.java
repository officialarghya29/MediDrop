package com.medidrop.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    Optional<Payment> findByClientReference(String clientReference);

    List<Payment> findByOrderId(Long orderId);

    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("select coalesce(sum(p.amount),0) from Payment p where p.status in (com.medidrop.payment.PaymentStatus.CAPTURED, com.medidrop.payment.PaymentStatus.PARTIALLY_REFUNDED)")
    BigDecimal sumCaptured();

    boolean existsByProviderPaymentIdAndStatus(String providerPaymentId, PaymentStatus status);
}
