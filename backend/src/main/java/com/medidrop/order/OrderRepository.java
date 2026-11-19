package com.medidrop.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findByOrderNumber(String orderNumber);

    Optional<Order> findByIdAndCustomerId(Long id, Long customerId);

    Optional<Order> findByIdAndPharmacyId(Long id, Long pharmacyId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    Page<Order> findByPharmacyIdOrderByCreatedAtDesc(Long pharmacyId, Pageable pageable);

    Page<Order> findByPharmacyIdAndStatusOrderByCreatedAtDesc(Long pharmacyId, OrderStatus status,
                                                              Pageable pageable);

    Optional<Order> findByTrackingKey(String trackingKey);

    List<Order> findTop5ByCustomerIdOrderByCreatedAtDesc(Long customerId);

    Page<Order> findByCustomerIdAndStatusIn(Long customerId, Collection<OrderStatus> statuses,
                                            Pageable pageable);

    long countByCustomerIdAndStatusIn(Long customerId, Collection<OrderStatus> statuses);

    Page<Order> findByStatusOrderByCreatedAtDesc(OrderStatus status, Pageable pageable);

    long countByStatus(OrderStatus status);

    long countByCustomerId(Long customerId);

    long countByPharmacyIdAndStatusIn(Long pharmacyId, Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status in :statuses and o.pharmacyId = :pharmacyId")
    BigDecimal sumRevenueForPharmacy(@Param("pharmacyId") Long pharmacyId,
                                     @Param("statuses") Collection<OrderStatus> statuses);

    @Query("select coalesce(sum(o.totalAmount), 0) from Order o where o.status in :statuses")
    BigDecimal sumPlatformRevenue(@Param("statuses") Collection<OrderStatus> statuses);

    @Query("select count(o) from Order o where o.createdAt >= :from")
    long countSince(@Param("from") Instant from);

    @Query("""
            select o from Order o
            where o.pharmacyId = :pharmacyId and o.createdAt >= :from
            """)
    List<Order> findPharmacyOrdersSince(@Param("pharmacyId") Long pharmacyId, @Param("from") Instant from);

    @Query("""
            select o from Order o
            where o.status = :status and o.createdAt < :before
            """)
    List<Order> findStaleByStatus(@Param("status") OrderStatus status, @Param("before") Instant before);

    @Query("select coalesce(avg(o.totalAmount), 0) from Order o where o.customerId = :customerId and o.status = com.medidrop.order.OrderStatus.DELIVERED")
    BigDecimal averageOrderValue(@Param("customerId") Long customerId);

    @Query("""
            select oi.medicineName, sum(oi.quantity)
            from OrderItem oi join Order o on o.id = oi.orderId
            where o.pharmacyId = :pharmacyId and o.status = com.medidrop.order.OrderStatus.DELIVERED
            group by oi.medicineName order by sum(oi.quantity) desc
            """)
    List<Object[]> topProductsForPharmacy(@Param("pharmacyId") Long pharmacyId, Pageable pageable);

    @Query("""
            select function('date', o.placedAt), count(o), coalesce(sum(o.totalAmount), 0)
            from Order o where o.placedAt >= :from
            group by function('date', o.placedAt)
            order by function('date', o.placedAt)
            """)
    List<Object[]> findTrend(@Param("from") Instant from);

    @Query("""
            select oi.medicineName, sum(oi.quantity)
            from OrderItem oi join Order o on o.id = oi.orderId
            where o.status = com.medidrop.order.OrderStatus.DELIVERED
            group by oi.medicineName order by sum(oi.quantity) desc
            """)
    List<Object[]> topPlatformMedicines();
}
