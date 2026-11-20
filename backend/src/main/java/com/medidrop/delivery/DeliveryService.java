package com.medidrop.delivery;

import com.medidrop.audit.AuditService;
import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.Crypto;
import com.medidrop.common.Haversine;
import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import com.medidrop.notification.NotificationService;
import com.medidrop.notification.SmsSender;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderService;
import com.medidrop.order.OrderStatus;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.security.Role;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryPartnerDocumentRepository documentRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryLocationRepository locationRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PharmacyRepository pharmacyRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;
    private final SmsSender smsSender;
    private final AuditService auditService;
    private final MedidropProperties properties;

    private static final List<AssignmentStatus> ACTIVE = List.of(AssignmentStatus.ASSIGNED,
            AssignmentStatus.ACCEPTED, AssignmentStatus.AT_PHARMACY, AssignmentStatus.PICKED_UP,
            AssignmentStatus.OUT_FOR_DELIVERY);

    @Transactional
    public void register(DeliveryDtos.RegistrationRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = new User();
        user.setFullName(request.fullName());
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.DELIVERY);
        user.setEmailVerified(false);
        userRepository.save(user);

        DeliveryPartner partner = new DeliveryPartner();
        partner.setUserId(user.getId());
        partner.setFullName(request.fullName());
        partner.setPhone(request.phone());
        partner.setEmail(email);
        partner.setAddress(request.address());
        partner.setIdProofType(request.idProofType());
        partner.setIdProofNumber(request.idProofNumber());
        partner.setVehicleType(request.vehicleType());
        partner.setVehicleNumber(request.vehicleNumber());
        partner.setBankAccountName(request.bankAccountName());
        partner.setBankAccountLast4(request.bankAccountLast4());
        partner.setPayoutIfsc(request.payoutIfsc());
        partner.setStatus(ApprovalStatus.PENDING);
        partner.setOnline(false);
        partnerRepository.save(partner);
        auditService.recordForUser(user.getId(), Role.DELIVERY.name(), "DELIVERY_PARTNER_REGISTERED",
                "DeliveryPartner", partner.getId(), "Awaiting approval");
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.PartnerProfile profile(Long userId) {
        return toProfile(requirePartner(userId));
    }

    @Transactional(readOnly = true)
    public DeliveryDtos.DashboardResponse dashboard(Long userId) {
        DeliveryPartner partner = requirePartner(userId);
        long pending = assignmentRepository.countByPartnerIdAndStatusIn(partner.getId(),
                List.of(AssignmentStatus.ASSIGNED));
        long activeCount = assignmentRepository.countByPartnerIdAndStatusIn(partner.getId(), ACTIVE);
        long completed = assignmentRepository.countByPartnerIdAndStatus(partner.getId(),
                AssignmentStatus.DELIVERED);
        Instant startOfDay = LocalDate.now(ZoneId.of("Asia/Kolkata")).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant();
        BigDecimal today = BigDecimal.valueOf(assignmentRepository.sumPayoutSince(partner.getId(), startOfDay));
        BigDecimal total = BigDecimal.valueOf(assignmentRepository.sumPayout(partner.getId()));
        List<DeliveryDtos.AssignmentRow> active = activeRows(partner.getId());
        return new DeliveryDtos.DashboardResponse(toProfile(partner), partner.isOnline(), pending,
                activeCount, completed, today, total, active);
    }

    @Transactional
    public DeliveryDtos.PartnerProfile setAvailability(Long userId, DeliveryDtos.AvailabilityRequest request) {
        DeliveryPartner partner = requirePartner(userId);
        if (Boolean.TRUE.equals(request.online()) && partner.getStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.forbidden("Your account is not approved yet");
        }
        if (Boolean.TRUE.equals(request.online())) {
            if (request.latitude() == null || request.longitude() == null) {
                throw ApiException.badRequest("Share your current location to go online");
            }
            partner.setCurrentLatitude(request.latitude());
            partner.setCurrentLongitude(request.longitude());
            partner.setLastLocationAt(Instant.now());
        }
        partner.setOnline(Boolean.TRUE.equals(request.online()));
        partnerRepository.save(partner);
        return toProfile(partner);
    }

    @Transactional(readOnly = true)
    public List<DeliveryDtos.AssignmentRow> assignments(Long userId, String status) {
        DeliveryPartner partner = requirePartner(userId);
        List<DeliveryAssignment> rows = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? assignmentRepository.findByPartnerIdOrderByCreatedAtDesc(partner.getId(),
                org.springframework.data.domain.PageRequest.of(0, 100)).getContent()
                : assignmentRepository.findByPartnerIdAndStatusIn(partner.getId(),
                List.of(parseStatus(status)));
        return rows.stream().map(this::toRow).toList();
    }

    @Transactional
    public DeliveryDtos.AssignmentRow accept(Long userId, Long assignmentId) {
        DeliveryPartner partner = requirePartner(userId);
        DeliveryAssignment assignment = assignmentRepository.findByIdAndPartnerId(assignmentId, partner.getId())
                .orElseThrow(() -> ApiException.notFound("Delivery not found"));
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED) {
            throw ApiException.conflict("This delivery can no longer be accepted");
        }
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        assignment.setAcceptedAt(Instant.now());
        assignmentRepository.save(assignment);
        notificationService.notifyUser(orderRepository.findById(assignment.getOrderId())
                        .map(Order::getCustomerId).orElse(0L), "DELIVERY", "Delivery partner assigned",
                "A MediDrop delivery partner accepted your delivery.", null);
        return toRow(assignment);
    }

    @Transactional
    public DeliveryDtos.AssignmentRow reject(Long userId, Long assignmentId, String reason) {
        DeliveryPartner partner = requirePartner(userId);
        DeliveryAssignment assignment = assignmentRepository.findByIdAndPartnerId(assignmentId, partner.getId())
                .orElseThrow(() -> ApiException.notFound("Delivery not found"));
        if (assignment.getStatus() != AssignmentStatus.ASSIGNED && assignment.getStatus() != AssignmentStatus.ACCEPTED) {
            throw ApiException.conflict("This delivery cannot be rejected at this stage");
        }
        assignment.setStatus(AssignmentStatus.REJECTED);
        assignment.setRejectionReason(reason);
        assignmentRepository.save(assignment);
        partner.setOnline(false);
        partnerRepository.save(partner);
        autoAssign(assignment.getOrderId(), partner.getId());
        return toRow(assignment);
    }

    @Transactional
    public DeliveryDtos.AssignmentRow updateStatus(Long userId, Long assignmentId,
                                                   DeliveryDtos.StatusRequest request) {
        DeliveryPartner partner = requirePartner(userId);
        DeliveryAssignment assignment = assignmentRepository.findByIdAndPartnerId(assignmentId, partner.getId())
                .orElseThrow(() -> ApiException.notFound("Delivery not found"));
        Order order = orderRepository.findById(assignment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        AssignmentStatus target = request.status();
        switch (target) {
            case AT_PHARMACY -> require(assignment, AssignmentStatus.ACCEPTED);
            case PICKED_UP -> {
                require(assignment, AssignmentStatus.AT_PHARMACY);
                orderService.systemTransition(order, OrderStatus.PICKED_UP, "Picked up by delivery partner");
                assignment.setPickedUpAt(Instant.now());
            }
            case OUT_FOR_DELIVERY -> {
                require(assignment, AssignmentStatus.PICKED_UP);
                orderService.systemTransition(order, OrderStatus.OUT_FOR_DELIVERY, "Out for delivery");
            }
            case FAILED -> {
                if (!ACTIVE.contains(assignment.getStatus())) {
                    throw ApiException.conflict("This delivery is already closed");
                }
                orderService.adminTransition(partner.getUserId(), order.getId(), OrderStatus.FAILED,
                        request.note() == null ? "Delivery failed" : request.note());
            }
            default -> throw ApiException.badRequest("Unsupported delivery status update");
        }
        assignment.setStatus(target);
        assignmentRepository.save(assignment);
        broadcast(order, assignment);
        return toRow(assignment);
    }

    @Transactional
    public DeliveryDtos.AssignmentRow complete(Long userId, Long assignmentId,
                                               DeliveryDtos.CompleteRequest request) {
        DeliveryPartner partner = requirePartner(userId);
        DeliveryAssignment assignment = assignmentRepository.findByIdAndPartnerId(assignmentId, partner.getId())
                .orElseThrow(() -> ApiException.notFound("Delivery not found"));
        Order order = orderRepository.findById(assignment.getOrderId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (assignment.getStatus() != AssignmentStatus.OUT_FOR_DELIVERY
                && assignment.getStatus() != AssignmentStatus.PICKED_UP) {
            throw ApiException.conflict("Mark the order out for delivery before completing it");
        }
        if (order.getDeliveryOtpHash() == null) {
            throw ApiException.conflict("This order has no delivery verification code");
        }
        if (!Crypto.constantTimeEquals(order.getDeliveryOtpHash(),
                Crypto.sha256Hex(request.otp().trim()))) {
            throw ApiException.badRequest("Incorrect delivery verification code");
        }
        order.setDeliveryOtpVerified(true);
        order.setDeliveryOtp(null);
        orderRepository.save(order);
        orderService.systemTransition(order, OrderStatus.DELIVERED, "Delivery confirmed with OTP");

        assignment.setStatus(AssignmentStatus.DELIVERED);
        assignment.setDeliveredAt(Instant.now());
        assignmentRepository.save(assignment);
        partner.setCompletedDeliveries(partner.getCompletedDeliveries() + 1);
        partnerRepository.save(partner);
        broadcast(order, assignment);
        auditService.recordForUser(userId, Role.DELIVERY.name(), "DELIVERY_COMPLETED", "Order", order.getId(),
                null);
        return toRow(assignment);
    }

    @Transactional
    public void pingLocation(Long userId, DeliveryDtos.LocationPing ping) {
        DeliveryPartner partner = requirePartner(userId);
        if (!partner.isOnline()) {
            throw ApiException.conflict("Go online before sharing your location");
        }
        DeliveryAssignment assignment = assignmentRepository
                .findByPartnerIdAndStatusIn(partner.getId(), ACTIVE).stream().findFirst().orElse(null);
        partner.setCurrentLatitude(ping.latitude());
        partner.setCurrentLongitude(ping.longitude());
        partner.setLastLocationAt(Instant.now());
        partnerRepository.save(partner);

        if (assignment != null) {
            DeliveryLocation location = new DeliveryLocation();
            location.setAssignmentId(assignment.getId());
            location.setPartnerId(partner.getId());
            location.setLatitude(ping.latitude());
            location.setLongitude(ping.longitude());
            location.setHeading(ping.heading());
            location.setSpeed(ping.speed());
            location.setAccuracy(ping.accuracy());
            locationRepository.save(location);
            orderRepository.findById(assignment.getOrderId()).ifPresent(order -> broadcast(order, assignment));
        }
    }

    /** Assigns the best available partner; safe to call when the order is ready for pickup. */
    @Transactional
    public boolean autoAssign(Long orderId) {
        return autoAssign(orderId, null);
    }

    private boolean autoAssign(Long orderId, Long excludePartnerId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.READY_FOR_PICKUP) return false;
        Pharmacy pharmacy = pharmacyRepository.findById(order.getPharmacyId()).orElse(null);
        if (pharmacy == null) return false;
        double radius = properties.getDelivery().getAssignmentSearchRadiusKm();
        double[] box = Haversine.boundingBox(pharmacy.getLatitude(), pharmacy.getLongitude(), radius);
        List<DeliveryPartner> candidates = partnerRepository
                .findOnlineInBox(box[0], box[1], box[2], box[3]).stream()
                .filter(p -> excludePartnerId == null || !p.getId().equals(excludePartnerId))
                .filter(p -> p.getCurrentLatitude() != null)
                .toList();
        DeliveryPartner best = null;
        double bestScore = Double.MAX_VALUE;
        for (DeliveryPartner candidate : candidates) {
            double distance = Haversine.distanceKm(pharmacy.getLatitude(), pharmacy.getLongitude(),
                    candidate.getCurrentLatitude(), candidate.getCurrentLongitude());
            if (distance > radius) continue;
            long load = assignmentRepository.countByPartnerIdAndStatusIn(candidate.getId(), ACTIVE);
            double score = distance + load * 2.5;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        if (best == null) {
            log.info("No delivery partner available for order {}", order.getOrderNumber());
            return false;
        }
        DeliveryAssignment assignment = assignmentRepository.findByOrderId(orderId).orElseGet(DeliveryAssignment::new);
        assignment.setOrderId(orderId);
        assignment.setPartnerId(best.getId());
        assignment.setStatus(AssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(Instant.now());
        assignment.setAcceptedAt(null);
        assignment.setPickedUpAt(null);
        assignment.setDeliveredAt(null);
        double distance = Haversine.distanceKm(pharmacy.getLatitude(), pharmacy.getLongitude(),
                order.getLatitude(), order.getLongitude());
        assignment.setDistanceKm(Math.round(distance * 10.0) / 10.0);
        assignment.setEstimatedMinutes((int) Math.max(10, Math.round(distance / 22.0 * 60) + 8));
        assignment.setPayout(properties.getDelivery().getBaseFee()
                .add(properties.getDelivery().getFeePerKm()
                        .multiply(BigDecimal.valueOf(Math.ceil(distance)))).intValue());
        assignmentRepository.save(assignment);

        String otp = Crypto.numericCode(4);
        order.setDeliveryOtpHash(Crypto.sha256Hex(otp));
        order.setDeliveryOtp(otp);
        order.setDeliveryOtpVerified(false);
        orderRepository.save(order);
        orderService.systemTransition(order, OrderStatus.DELIVERY_ASSIGNED, "Delivery partner assigned");

        smsSender.send(order.getRecipientPhone(), "Your MediDrop delivery code is " + otp
                + ". Share it with the delivery partner only after receiving your order.");
        notificationService.notifyUser(order.getCustomerId(), "DELIVERY", "Out for delivery soon",
                "Order " + order.getOrderNumber() + " is assigned to " + best.getFullName()
                        + ". Your delivery verification code is " + otp + ".",
                "/orders/" + order.getId());
        notificationService.notifyUser(best.getUserId(), "DELIVERY", "New delivery assigned",
                "Order " + order.getOrderNumber() + " is ready for pickup.", "/delivery");
        broadcast(order, assignment);
        return true;
    }

    @Transactional
    public void adminDecision(Long partnerId, boolean approve, String note, Long adminId) {
        DeliveryPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> ApiException.notFound("Delivery partner not found"));
        partner.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        partner.setReviewNote(note);
        partnerRepository.save(partner);
        auditService.recordForUser(adminId, Role.ADMIN.name(),
                approve ? "DELIVERY_PARTNER_APPROVED" : "DELIVERY_PARTNER_REJECTED",
                "DeliveryPartner", partnerId, note);
        notificationService.notifyUser(partner.getUserId(), "ACCOUNT",
                approve ? "Delivery account approved" : "Delivery application update",
                approve ? "You can now go online and accept deliveries."
                        : "Your application was not approved. " + (note == null ? "" : note), "/delivery");
    }

    @Transactional
    public void suspend(Long partnerId, Long adminId, String note) {
        DeliveryPartner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> ApiException.notFound("Delivery partner not found"));
        partner.setStatus(ApprovalStatus.SUSPENDED);
        partner.setOnline(false);
        partner.setReviewNote(note);
        partnerRepository.save(partner);
        auditService.recordForUser(adminId, Role.ADMIN.name(), "DELIVERY_PARTNER_SUSPENDED",
                "DeliveryPartner", partnerId, note);
    }

    private void require(DeliveryAssignment assignment, AssignmentStatus expected) {
        if (assignment.getStatus() != expected) {
            throw ApiException.conflict("Expected delivery status " + expected + " but was "
                    + assignment.getStatus());
        }
    }

    private void broadcast(Order order, DeliveryAssignment assignment) {
        partnerRepository.findById(assignment.getPartnerId()).ifPresent(partner -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("orderNumber", order.getOrderNumber());
            payload.put("status", order.getStatus().name());
            payload.put("deliveryStatus", assignment.getStatus().name());
            payload.put("latitude", partner.getCurrentLatitude());
            payload.put("longitude", partner.getCurrentLongitude());
            payload.put("updatedAt", Instant.now().toString());
            payload.put("estimatedMinutes", assignment.getEstimatedMinutes());
            if (order.getTrackingKey() != null) {
                messagingTemplate.convertAndSend("/topic/tracking/" + order.getTrackingKey(), payload);
            }
            messagingTemplate.convertAndSend("/topic/delivery/" + partner.getId(), payload);
        });
    }

    private AssignmentStatus parseStatus(String value) {
        try {
            return AssignmentStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown delivery status: " + value);
        }
    }

    private DeliveryPartner requirePartner(Long userId) {
        return partnerRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Delivery partner profile not found"));
    }

    private List<DeliveryDtos.AssignmentRow> activeRows(Long partnerId) {
        return assignmentRepository.findByPartnerIdAndStatusIn(partnerId, ACTIVE).stream()
                .map(this::toRow).toList();
    }

    private DeliveryDtos.AssignmentRow toRow(DeliveryAssignment assignment) {
        Order order = orderRepository.findById(assignment.getOrderId()).orElse(null);
        Pharmacy pharmacy = order == null ? null
                : pharmacyRepository.findById(order.getPharmacyId()).orElse(null);
        return new DeliveryDtos.AssignmentRow(assignment.getId(), assignment.getOrderId(),
                order == null ? null : order.getOrderNumber(), assignment.getStatus().name(),
                pharmacy == null ? null : pharmacy.getName(),
                pharmacy == null ? null : pharmacy.getRegisteredAddress(),
                pharmacy == null ? null : pharmacy.getLatitude(),
                pharmacy == null ? null : pharmacy.getLongitude(),
                order == null ? null : order.getLine1() + ", " + order.getCity(),
                order == null ? null : order.getLatitude(), order == null ? null : order.getLongitude(),
                assignment.getDistanceKm(), assignment.getEstimatedMinutes(), assignment.getPayout(),
                assignment.getAssignedAt(), order != null && order.isPrescriptionRequired(),
                order != null && order.isDeliveryOtpVerified(), order == null ? null : order.getTrackingKey());
    }

    private DeliveryDtos.PartnerProfile toProfile(DeliveryPartner p) {
        return new DeliveryDtos.PartnerProfile(p.getId(), p.getUserId(), p.getFullName(), p.getPhone(),
                p.getEmail(), p.getAddress(), p.getVehicleType(), p.getVehicleNumber(), p.getStatus(),
                p.isOnline(), p.getCurrentLatitude(), p.getCurrentLongitude(), p.getLastLocationAt(),
                p.getCompletedDeliveries(), p.getRating(), p.getReviewNote());
    }
}
