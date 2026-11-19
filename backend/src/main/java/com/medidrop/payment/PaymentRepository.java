package com.medidrop.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByProviderOrderId(String providerOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.providerOrderId = :providerOrderId")
    Optional<Payment> findForUpdateByProviderOrderId(@Param("providerOrderId") String providerOrderId);

    Optional<Payment> findByClientReference(String clientReference);

    List<Payment> findByOrderId(Long orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p from Payment p
            where p.orderId = :orderId
              and p.status in (com.medidrop.payment.PaymentStatus.CAPTURED,
                               com.medidrop.payment.PaymentStatus.PARTIALLY_REFUNDED)
            order by p.id
            """)
    List<Payment> findRefundableForUpdate(@Param("orderId") Long orderId);

    List<Payment> findByCustomerIdOrderByCreatedAtDesc(Long customerId);

    @Query("select coalesce(sum(p.amount),0) from Payment p where p.status in (com.medidrop.payment.PaymentStatus.CAPTURED, com.medidrop.payment.PaymentStatus.PARTIALLY_REFUNDED)")
    BigDecimal sumCaptured();

    boolean existsByProviderPaymentIdAndStatus(String providerPaymentId, PaymentStatus status);
}
