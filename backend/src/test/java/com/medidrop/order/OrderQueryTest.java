package com.medidrop.order;

import com.medidrop.common.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers the customer-facing order queries: each tab must filter in SQL, paginate
 * correctly and count only its own status group. Tracking must resolve by key rather
 * than scanning the whole orders table.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderQueryTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    private Order seed(String tag, OrderStatus status, Long customerId) {
        Order order = new Order();
        order.setOrderNumber("MDTEST" + tag + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setCustomerId(customerId);
        order.setPharmacyId(1L);
        order.setStatus(status);
        order.setRecipientName("Test Recipient");
        order.setRecipientPhone("9800000000");
        order.setLine1("1 Test Lane");
        order.setCity("Kolkata");
        order.setState("West Bengal");
        order.setPincode("700001");
        order.setLatitude(22.5726);
        order.setLongitude(88.3639);
        order.setTotalAmount(new BigDecimal("149.00"));
        order.setTrackingKey("trk" + UUID.randomUUID().toString().replace("-", ""));
        return orderRepository.save(order);
    }

    @Test
    void customerFiltersCountsAndPaginationStayConsistent() {
        Long customerId = 990_001L;
        seed("A", OrderStatus.PAYMENT_CONFIRMED, customerId);
        seed("B", OrderStatus.DELIVERED, customerId);
        seed("C", OrderStatus.CANCELLED, customerId);
        seed("D", OrderStatus.FAILED, customerId);

        var active = orderService.customerOrders(customerId, "active", 0, 1);
        assertThat(active.content()).hasSize(1);
        assertThat(active.content().get(0).status()).isEqualTo("PAYMENT_CONFIRMED");
        assertThat(active.totalElements()).isEqualTo(1);

        var delivered = orderService.customerOrders(customerId, "delivered", 0, 20);
        assertThat(delivered.content()).extracting(OrderDtos.OrderSummary::status)
                .containsExactly("DELIVERED");

        var cancelled = orderService.customerOrders(customerId, "cancelled", 0, 20);
        assertThat(cancelled.content()).extracting(OrderDtos.OrderSummary::status)
                .containsExactlyInAnyOrder("CANCELLED", "FAILED");

        var counts = orderService.counts(customerId);
        assertThat(counts.total()).isEqualTo(4);
        assertThat(counts.active()).isEqualTo(1);
        assertThat(counts.delivered()).isEqualTo(1);
        assertThat(counts.cancelled()).isEqualTo(2);

        assertThatThrownBy(() -> orderService.customerOrders(customerId, "bogus", 0, 20))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unsupported order filter");
        assertThatThrownBy(() -> orderService.adminOrders("NOT_A_STATUS", 0, 20))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Unknown order status");
    }

    @Test
    void trackingResolvesByUnpredictableKey() {
        Long customerId = 990_002L;
        Order order = seed("T", OrderStatus.OUT_FOR_DELIVERY, customerId);

        var tracking = orderService.tracking(order.getTrackingKey());

        assertThat(tracking.orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(tracking.status()).isEqualTo("OUT_FOR_DELIVERY");
        assertThat(tracking.destinationLabel()).isEqualTo("Delivery area, Kolkata");
        assertThat(tracking.destinationLatitude()).isEqualTo(22.573);
        assertThat(tracking.destinationLongitude()).isEqualTo(88.364);
        assertThatThrownBy(() -> orderService.tracking("does-not-exist"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Tracking link is invalid");
    }
}
