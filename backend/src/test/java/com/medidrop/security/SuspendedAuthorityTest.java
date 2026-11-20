package com.medidrop.security;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.exception.ApiException;
import com.medidrop.delivery.AssignmentStatus;
import com.medidrop.delivery.DeliveryAssignment;
import com.medidrop.delivery.DeliveryAssignmentRepository;
import com.medidrop.delivery.DeliveryPartner;
import com.medidrop.delivery.DeliveryPartnerRepository;
import com.medidrop.delivery.DeliveryService;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderService;
import com.medidrop.order.OrderStatus;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.prescription.Prescription;
import com.medidrop.prescription.PrescriptionDtos;
import com.medidrop.prescription.PrescriptionRepository;
import com.medidrop.prescription.PrescriptionService;
import com.medidrop.prescription.PrescriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SuspendedAuthorityTest {

    private static final AtomicLong IDS = new AtomicLong(9_300_000L);

    @Autowired private DeliveryService deliveryService;
    @Autowired private DeliveryPartnerRepository partnerRepository;
    @Autowired private DeliveryAssignmentRepository assignmentRepository;
    @Autowired private PharmacyRepository pharmacyRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderService orderService;
    @Autowired private PrescriptionService prescriptionService;
    @Autowired private PrescriptionRepository prescriptionRepository;

    @Test
    void suspendedRiderCannotAcceptAnAssignedDelivery() {
        Pharmacy pharmacy = pharmacy(ApprovalStatus.APPROVED, true);
        Order order = order(pharmacy.getId(), OrderStatus.DELIVERY_ASSIGNED);
        DeliveryPartner rider = partner(ApprovalStatus.SUSPENDED);

        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setOrderId(order.getId());
        assignment.setPartnerId(rider.getId());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(Instant.now());
        DeliveryAssignment saved = assignmentRepository.save(assignment);

        assertThatThrownBy(() -> deliveryService.accept(rider.getUserId(), saved.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void suspendedPharmacyCannotAcceptAnExistingOrder() {
        Pharmacy pharmacy = pharmacy(ApprovalStatus.SUSPENDED, false);
        Order order = order(pharmacy.getId(), OrderStatus.PAYMENT_CONFIRMED);

        assertThatThrownBy(() -> orderService.pharmacyTransition(pharmacy.getOwnerUserId(), order.getId(),
                OrderStatus.PHARMACY_ACCEPTED, "Attempt after suspension"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void suspendedPharmacyCannotReviewAQueuedPrescription() {
        Pharmacy pharmacy = pharmacy(ApprovalStatus.SUSPENDED, false);
        Prescription prescription = prescription(pharmacy);

        assertThatThrownBy(() -> prescriptionService.review(pharmacy.getOwnerUserId(), prescription.getId(),
                new PrescriptionDtos.ReviewDecisionRequest(true, "Approved after suspension", null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("must be active");
    }

    @Test
    void suspendedPharmacyCannotOpenOrReadItsPrescriptionQueue() {
        Pharmacy pharmacy = pharmacy(ApprovalStatus.SUSPENDED, false);
        Prescription prescription = prescription(pharmacy);

        assertThatThrownBy(() -> prescriptionService.pharmacyQueue(pharmacy.getOwnerUserId(), "UPLOADED", 0, 20))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
        assertThatThrownBy(() -> prescriptionService.reviews(pharmacy.getOwnerUserId(), prescription.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void suspendedPharmacyCannotReadPrescriptionFile() {
        Pharmacy pharmacy = pharmacy(ApprovalStatus.SUSPENDED, false);
        Prescription prescription = prescription(pharmacy);

        assertThatThrownBy(() -> prescriptionService.loadFile(pharmacy.getOwnerUserId(), Role.PHARMACY,
                prescription.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("cannot access");
    }

    @Test
    void suspendedPharmacyCannotBeSelectedForPrescriptionUpload() {
        Pharmacy pharmacy = pharmacy(ApprovalStatus.SUSPENDED, false);
        MockMultipartFile file = new MockMultipartFile("file", "rx.jpg", "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00});

        assertThatThrownBy(() -> prescriptionService.upload(IDS.incrementAndGet(), file, "queued after suspension",
                pharmacy.getId()))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("not available");
    }

    private Pharmacy pharmacy(ApprovalStatus status, boolean active) {
        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setOwnerUserId(IDS.incrementAndGet());
        pharmacy.setName("State Authority Pharmacy");
        pharmacy.setLicenseNumber("WB-STATE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        pharmacy.setRegisteredAddress("1 Test Road");
        pharmacy.setCity("Kolkata");
        pharmacy.setState("West Bengal");
        pharmacy.setPincode("700001");
        pharmacy.setLatitude(22.5726);
        pharmacy.setLongitude(88.3639);
        pharmacy.setPhone(uniquePhone());
        pharmacy.setEmail("state-" + UUID.randomUUID() + "@example.test");
        pharmacy.setStatus(status);
        pharmacy.setActive(active);
        pharmacy.setAcceptingOrders(active);
        return pharmacyRepository.save(pharmacy);
    }

    private Prescription prescription(Pharmacy pharmacy) {
        Prescription prescription = new Prescription();
        prescription.setCustomerId(IDS.incrementAndGet());
        prescription.setFileName("rx.png");
        prescription.setStorageKey("test/" + UUID.randomUUID());
        prescription.setContentType("image/png");
        prescription.setSizeBytes(12L);
        prescription.setPharmacyId(pharmacy.getId());
        prescription.setStatus(PrescriptionStatus.UPLOADED);
        return prescriptionRepository.save(prescription);
    }

    private Order order(Long pharmacyId, OrderStatus status) {
        Order order = new Order();
        order.setOrderNumber("MDSTATE" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setCustomerId(IDS.incrementAndGet());
        order.setPharmacyId(pharmacyId);
        order.setStatus(status);
        order.setRecipientName("State Authority Customer");
        order.setRecipientPhone(uniquePhone());
        order.setLine1("1 Test Lane");
        order.setCity("Kolkata");
        order.setState("West Bengal");
        order.setPincode("700001");
        order.setLatitude(22.5726);
        order.setLongitude(88.3639);
        order.setSubtotal(new BigDecimal("100.00"));
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setTrackingKey("state" + UUID.randomUUID().toString().replace("-", ""));
        return orderRepository.save(order);
    }

    private DeliveryPartner partner(ApprovalStatus status) {
        DeliveryPartner partner = new DeliveryPartner();
        partner.setUserId(IDS.incrementAndGet());
        partner.setFullName("Suspended Rider");
        partner.setPhone(uniquePhone());
        partner.setEmail("rider-" + UUID.randomUUID() + "@example.test");
        partner.setAddress("Kolkata");
        partner.setVehicleType("Motorcycle");
        partner.setStatus(status);
        partner.setOnline(false);
        return partnerRepository.save(partner);
    }

    private String uniquePhone() {
        long tail = IDS.incrementAndGet() % 1_000_000_000L;
        return "9" + String.format("%09d", tail);
    }
}
