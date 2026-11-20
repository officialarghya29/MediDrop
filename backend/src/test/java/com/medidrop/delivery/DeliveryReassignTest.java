package com.medidrop.delivery;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderStatus;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DeliveryReassignTest {

    private static final AtomicLong IDS = new AtomicLong(9_200_000L);

    @Autowired private DeliveryService deliveryService;
    @Autowired private DeliveryPartnerRepository partnerRepository;
    @Autowired private DeliveryAssignmentRepository assignmentRepository;
    @Autowired private PharmacyRepository pharmacyRepository;
    @Autowired private OrderRepository orderRepository;

    @Test
    void rejectingAnAssignedDeliveryReplacesTheRiderWithoutMovingTheOrderBackwards() {
        Pharmacy pharmacy = pharmacy();
        Order order = order(pharmacy.getId());

        DeliveryPartner first = partner(22.5700, 88.3600, "First Rider");
        DeliveryPartner second = partner(22.5740, 88.3670, "Second Rider");

        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setOrderId(order.getId());
        assignment.setPartnerId(first.getId());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(Instant.now());
        assignment = assignmentRepository.save(assignment);

        deliveryService.reject(first.getUserId(), assignment.getId(), "Vehicle breakdown");

        DeliveryAssignment replacement = assignmentRepository.findByOrderId(order.getId()).orElseThrow();
        assertThat(replacement.getPartnerId()).isEqualTo(second.getId());
        assertThat(replacement.getStatus()).isEqualTo(AssignmentStatus.ASSIGNED);
        assertThat(replacement.getRejectionReason()).isNull();
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.DELIVERY_ASSIGNED);
        assertThat(orderRepository.findById(order.getId()).orElseThrow().getDeliveryOtp()).isNotBlank();
        assertThat(partnerRepository.findById(first.getId()).orElseThrow().isOnline()).isFalse();
    }

    private Pharmacy pharmacy() {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setOwnerUserId(IDS.incrementAndGet());
        pharmacy.setName("Reassign Test Pharmacy");
        pharmacy.setLicenseNumber("WB-REASSIGN-" + UUID.randomUUID().toString().substring(0, 8));
        pharmacy.setRegisteredAddress("1 Test Road");
        pharmacy.setCity("Kolkata");
        pharmacy.setState("West Bengal");
        pharmacy.setPincode("700001");
        pharmacy.setLatitude(22.5726);
        pharmacy.setLongitude(88.3639);
        pharmacy.setPhone("9800000000");
        pharmacy.setEmail("reassign-" + UUID.randomUUID() + "@example.test");
        pharmacy.setStatus(ApprovalStatus.APPROVED);
        pharmacy.setActive(true);
        pharmacy.setAcceptingOrders(true);
        return pharmacyRepository.save(pharmacy);
    }

    private Order order(Long pharmacyId) {
        Order order = new Order();
        order.setOrderNumber("MDREASSIGN" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setCustomerId(IDS.incrementAndGet());
        order.setPharmacyId(pharmacyId);
        order.setStatus(OrderStatus.DELIVERY_ASSIGNED);
        order.setRecipientName("Reassign Customer");
        order.setRecipientPhone("9800000000");
        order.setLine1("1 Test Lane");
        order.setCity("Kolkata");
        order.setState("West Bengal");
        order.setPincode("700001");
        order.setLatitude(22.5726);
        order.setLongitude(88.3639);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setTrackingKey("reassign" + UUID.randomUUID().toString().replace("-", ""));
        return orderRepository.save(order);
    }

    private DeliveryPartner partner(double latitude, double longitude, String name) {
        DeliveryPartner partner = new DeliveryPartner();
        partner.setUserId(IDS.incrementAndGet());
        partner.setFullName(name);
        partner.setPhone("9800000000");
        partner.setEmail(name.replace(" ", ".").toLowerCase() + "-" + UUID.randomUUID() + "@example.test");
        partner.setAddress("Kolkata");
        partner.setVehicleType("Motorcycle");
        partner.setStatus(ApprovalStatus.APPROVED);
        partner.setOnline(true);
        partner.setCurrentLatitude(latitude);
        partner.setCurrentLongitude(longitude);
        partner.setLastLocationAt(Instant.now());
        return partnerRepository.save(partner);
    }
}
