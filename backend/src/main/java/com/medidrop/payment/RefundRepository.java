package com.medidrop.payment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    Page<Refund> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("select coalesce(sum(r.amount),0) from Refund r where r.status = 'PROCESSED'")
    BigDecimal sumProcessed();

    @Query("select coalesce(sum(r.amount),0) from Refund r where r.orderId = :orderId and r.status = 'PROCESSED'")
    BigDecimal sumProcessedForOrder(@Param("orderId") Long orderId);
}
