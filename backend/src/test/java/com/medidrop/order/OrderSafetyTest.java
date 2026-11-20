package com.medidrop.order;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.exception.ApiException;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderSafetyTest {

    @Autowired private OrderService orderService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private PharmacyRepository pharmacyRepository;

    private Pharmacy pharmacy(long ownerId) {
        Pharmacy p = new Pharmacy();
        p.setOwnerUserId(ownerId);
        p.setName("Test pharmacy " + ownerId);
        p.setLicenseNumber("WB-TEST-" + UUID.randomUUID().toString().substring(0, 8));
        p.setRegisteredAddress("1 Test Road");
        p.setCity("Kolkata");
        p.setState("West Bengal");
        p.setPincode("700001");
        p.setLatitude(22.5726);
        p.setLongitude(88.3639);
        p.setPhone("9800000000");
        p.setEmail("pharmacy" + ownerId + "@example.test");
        p.setStatus(ApprovalStatus.APPROVED);
        return pharmacyRepository.save(p);
    }

    private Order order(Long pharmacyId) {
        Order o = new Order();
        o.setOrderNumber("MDSEC" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        o.setCustomerId(700_001L);
        o.setPharmacyId(pharmacyId);
        o.setStatus(OrderStatus.DELIVERED);
        o.setRecipientName("Test Recipient");
        o.setRecipientPhone("9800000000");
        o.setLine1("1 Test Lane");
        o.setCity("Kolkata");
        o.setState("West Bengal");
        o.setPincode("700001");
        o.setLatitude(22.5726);
        o.setLongitude(88.3639);
        o.setSubtotal(new BigDecimal("100.00"));
        o.setTotalAmount(new BigDecimal("100.00"));
        o.setTrackingKey("sec" + UUID.randomUUID().toString().replace("-", ""));
        return orderRepository.save(o);
    }

    @Test
    void invoicesAreScopedToTheCallingPharmacyAndCustomer() {
        Pharmacy mine = pharmacy(710_001L);
        pharmacy(710_002L);
        Order order = order(mine.getId());

        assertThat(orderService.pharmacyInvoice(710_001L, order.getId()).orderNumber())
                .isEqualTo(order.getOrderNumber());
        assertThat(orderService.customerInvoice(700_001L, order.getId()).orderNumber())
                .isEqualTo(order.getOrderNumber());

        assertThatThrownBy(() -> orderService.pharmacyInvoice(710_002L, order.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Order not found");
        assertThatThrownBy(() -> orderService.customerInvoice(700_002L, order.getId()))
                .isInstanceOf(ApiException.class).hasMessageContaining("Order not found");
    }

    @Test
    void adminCannotJumpDeliveredOrderStraightToRefunded() {
        Pharmacy p = pharmacy(720_001L);
        Order order = order(p.getId());

        assertThatThrownBy(() -> orderService.adminTransition(1L, order.getId(),
                OrderStatus.REFUNDED, "manual override"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("refund endpoint");
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }
}
