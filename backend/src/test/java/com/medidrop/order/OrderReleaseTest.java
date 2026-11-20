package com.medidrop.order;

import com.medidrop.inventory.InventoryTransaction;
import com.medidrop.inventory.InventoryTransactionRepository;
import com.medidrop.inventory.PharmacyInventory;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.promo.Coupon;
import com.medidrop.promo.CouponRepository;
import com.medidrop.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Closing an order before handover must put stock and coupon capacity back exactly
 * once. Without this, cancelled prescriptions silently consume inventory forever.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderReleaseTest {

    private static final AtomicLong IDS = new AtomicLong(8_100_000L);

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private PharmacyInventoryRepository inventoryRepository;
    @Autowired private InventoryTransactionRepository transactionRepository;
    @Autowired private CouponRepository couponRepository;

    private record Fixture(Order order, OrderItem item, PharmacyInventory inventory, Coupon coupon) {
    }

    private Fixture fixture(OrderStatus status) {
        long pharmacyId = IDS.incrementAndGet();
        PharmacyInventory inventory = new PharmacyInventory();
        inventory.setPharmacyId(pharmacyId);
        inventory.setMedicineId(IDS.incrementAndGet());
        inventory.setPrice(new BigDecimal("100.00"));
        inventory.setMrp(new BigDecimal("120.00"));
        inventory.setStockQuantity(8);
        inventory.setReservedQuantity(0);
        inventory.setLowStockThreshold(2);
        inventory.setActive(true);
        inventory = inventoryRepository.save(inventory);

        Coupon coupon = new Coupon();
        coupon.setCode("REL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        coupon.setType(Coupon.Type.PERCENT);
        coupon.setValue(new BigDecimal("10"));
        coupon.setMinOrderValue(BigDecimal.ZERO);
        coupon.setActive(true);
        coupon.setUsageLimit(10);
        coupon.setUsedCount(4);
        coupon = couponRepository.save(coupon);

        Order order = new Order();
        order.setOrderNumber("MDREL" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setCustomerId(IDS.incrementAndGet());
        order.setPharmacyId(pharmacyId);
        order.setStatus(status);
        order.setRecipientName("Test Recipient");
        order.setRecipientPhone("9800000000");
        order.setLine1("1 Test Lane");
        order.setCity("Kolkata");
        order.setState("West Bengal");
        order.setPincode("700001");
        order.setLatitude(22.5726);
        order.setLongitude(88.3639);
        order.setSubtotal(new BigDecimal("200.00"));
        order.setTotalAmount(new BigDecimal("210.00"));
        order.setCouponCode(coupon.getCode());
        order.setTrackingKey("rel" + UUID.randomUUID().toString().replace("-", ""));
        order = orderRepository.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setMedicineId(inventory.getMedicineId());
        item.setMedicineName("Test medicine");
        item.setInventoryId(inventory.getId());
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setQuantity(2);
        item.setLineTotal(new BigDecimal("200.00"));
        item.setPrescriptionRequired(status == OrderStatus.PRESCRIPTION_REVIEW_PENDING);
        item = orderItemRepository.save(item);

        return new Fixture(order, item, inventory, coupon);
    }

    @Test
    void cancellationReleasesInventoryAndCouponOnce() {
        Fixture f = fixture(OrderStatus.PHARMACY_ACCEPTED);

        orderService.transition(f.order(), OrderStatus.CANCELLED, Role.ADMIN, 1L, "Customer requested cancellation");

        assertThat(inventoryRepository.findById(f.inventory().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        assertThat(couponRepository.findById(f.coupon().getId()).orElseThrow().getUsedCount())
                .isEqualTo(3);
        assertThat(orderRepository.findById(f.order().getId()).orElseThrow().isStockRestored()).isTrue();
        assertThat(orderRepository.findById(f.order().getId()).orElseThrow().isCouponReleased()).isTrue();
        assertThat(transactionRepository.findTop50ByInventoryIdOrderByCreatedAtDesc(f.inventory().getId()))
                .singleElement()
                .satisfies(tx -> {
                    assertThat(tx.getType()).isEqualTo(InventoryTransaction.Type.RETURN);
                    assertThat(tx.getQuantityDelta()).isEqualTo(2);
                });

        assertThatThrownBy(() -> orderService.transition(f.order(), OrderStatus.CANCELLED,
                Role.ADMIN, 1L, "Duplicate close"))
                .hasMessageContaining("Cannot move an order");
        assertThat(inventoryRepository.findById(f.inventory().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        assertThat(couponRepository.findById(f.coupon().getId()).orElseThrow().getUsedCount())
                .isEqualTo(3);
    }

    @Test
    void prescriptionRejectionAlsoReleasesInventoryAndCoupon() {
        Fixture f = fixture(OrderStatus.PRESCRIPTION_REVIEW_PENDING);

        orderService.transition(f.order(), OrderStatus.PRESCRIPTION_REJECTED, Role.PHARMACY, 2L,
                "Prescription illegible");

        assertThat(inventoryRepository.findById(f.inventory().getId()).orElseThrow().getStockQuantity())
                .isEqualTo(10);
        assertThat(couponRepository.findById(f.coupon().getId()).orElseThrow().getUsedCount())
                .isEqualTo(3);
        assertThat(orderRepository.findById(f.order().getId()).orElseThrow().isStockRestored()).isTrue();
        assertThat(orderRepository.findById(f.order().getId()).orElseThrow().isCouponReleased()).isTrue();
    }
}
