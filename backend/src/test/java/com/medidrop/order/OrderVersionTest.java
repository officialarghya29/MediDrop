package com.medidrop.order;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderVersionTest {

    @Autowired private OrderRepository orderRepository;
    @Autowired private EntityManager entityManager;

    @Test
    void staleOrderWriteCannotOverwriteANewerLifecycleTransition() {
        Order order = new Order();
        order.setOrderNumber("MDVER" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setCustomerId(9_100_001L);
        order.setPharmacyId(9_100_002L);
        order.setStatus(OrderStatus.PAYMENT_CONFIRMED);
        order.setRecipientName("Version Test");
        order.setRecipientPhone("9800000000");
        order.setLine1("1 Test Lane");
        order.setCity("Kolkata");
        order.setState("West Bengal");
        order.setPincode("700001");
        order.setLatitude(22.5726);
        order.setLongitude(88.3639);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setTrackingKey("ver" + UUID.randomUUID().toString().replace("-", ""));
        order = orderRepository.saveAndFlush(order);

        Order stale = orderRepository.findById(order.getId()).orElseThrow();
        entityManager.detach(stale);

        Order current = orderRepository.findById(order.getId()).orElseThrow();
        current.setCustomerNote("first writer");
        orderRepository.saveAndFlush(current);
        assertThat(current.getVersion()).isEqualTo(1L);

        stale.setCustomerNote("stale writer");
        assertThatThrownBy(() -> orderRepository.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }
}
