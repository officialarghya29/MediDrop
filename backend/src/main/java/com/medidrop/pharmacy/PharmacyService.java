package com.medidrop.pharmacy;

import com.medidrop.audit.AuditService;
import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.Haversine;
import com.medidrop.common.exception.ApiException;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderStatus;
import com.medidrop.prescription.PrescriptionRepository;
import com.medidrop.prescription.PrescriptionStatus;
import com.medidrop.security.Role;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PharmacyService {

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyOperatingHourRepository hoursRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<PharmacyDtos.NearbyPharmacy> nearby(double lat, double lng, double radiusKm, boolean requireStock) {
        double radius = Math.min(Math.max(radiusKm, 1), 50);
        double[] box = Haversine.boundingBox(lat, lng, radius);
        return pharmacyRepository.findCandidatesInBox(box[0], box[1], box[2], box[3]).stream()
                .map(p -> {
                    double distance = Haversine.distanceKm(lat, lng, p.getLatitude(), p.getLongitude());
                    boolean inArea = distance <= p.getDeliveryRadiusKm();
                    return new PharmacyDtos.NearbyPharmacy(p.getId(), p.getName(), p.getRegisteredAddress(),
                            p.getCity(), p.getPincode(), p.getLatitude(), p.getLongitude(),
                            Math.round(distance * 10.0) / 10.0, p.getDeliveryRadiusKm(), p.getMinOrderValue(),
                            p.getRating(), p.isAcceptingOrders(), inArea, p.getAvgPrepMinutes());
                })
                .filter(p -> p.distanceKm() <= radius)
                .filter(p -> !requireStock || p.withinDeliveryArea())
                .sorted(Comparator.comparingDouble(PharmacyDtos.NearbyPharmacy::distanceKm))
                .toList();
    }

    @Transactional(readOnly = true)
    public PharmacyDtos.PharmacyDetail detail(Long id) {
        Pharmacy p = pharmacyRepository.findById(id)
                .filter(x -> x.getStatus() == ApprovalStatus.APPROVED && x.isActive())
                .orElseThrow(() -> ApiException.notFound("Pharmacy not found"));
        List<PharmacyDtos.HourResponse> hours = hoursRepository.findByPharmacyIdOrderByDayOfWeek(id).stream()
                .map(h -> new PharmacyDtos.HourResponse(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime(),
                        h.isClosed()))
                .toList();
        return new PharmacyDtos.PharmacyDetail(p.getId(), p.getName(), p.getRegisteredAddress(), p.getCity(),
                p.getState(), p.getPincode(), p.getLatitude(), p.getLongitude(), p.getRating(),
                p.getMinOrderValue(), p.getDeliveryRadiusKm(), p.getPhone(), hours);
    }

    @Transactional
    public PharmacyDtos.PharmacyDetail updateHours(Long userId, PharmacyDtos.OpeningHoursRequest request) {
        Pharmacy pharmacy = requirePharmacy(userId);
        hoursRepository.deleteByPharmacyId(pharmacy.getId());
        request.hours().forEach(h -> {
            PharmacyOperatingHour entity = new PharmacyOperatingHour();
            entity.setPharmacyId(pharmacy.getId());
            entity.setDayOfWeek(h.dayOfWeek());
            entity.setOpenTime(h.openTime());
            entity.setCloseTime(h.closeTime());
            entity.setClosed(h.closed());
            hoursRepository.save(entity);
        });
        return detail(pharmacy.getId());
    }

    @Transactional
    public PharmacyDtos.RegistrationRequest register(PharmacyDtos.RegistrationRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        if (pharmacyRepository.existsByLicenseNumberIgnoreCase(request.licenseNumber())) {
            throw ApiException.conflict("This drug licence number is already registered");
        }
        User owner = new User();
        owner.setFullName(request.ownerName());
        owner.setEmail(email);
        owner.setPhone(request.phone());
        owner.setPasswordHash(passwordEncoder.encode(request.password()));
        owner.setRole(Role.PHARMACY);
        owner.setEmailVerified(false);
        userRepository.save(owner);

        Pharmacy pharmacy = new Pharmacy();
        pharmacy.setOwnerUserId(owner.getId());
        pharmacy.setName(request.pharmacyName());
        pharmacy.setLicenseNumber(request.licenseNumber().trim().toUpperCase());
        pharmacy.setGstin(request.gstin());
        pharmacy.setRegisteredAddress(request.registeredAddress());
        pharmacy.setCity(request.city());
        pharmacy.setState(request.state());
        pharmacy.setPincode(request.pincode());
        pharmacy.setLatitude(request.latitude());
        pharmacy.setLongitude(request.longitude());
        pharmacy.setPhone(request.phone());
        pharmacy.setEmail(email);
        pharmacy.setStatus(ApprovalStatus.PENDING);
        pharmacy.setActive(false);
        pharmacy.setAcceptingOrders(false);
        if (request.deliveryRadiusKm() != null) pharmacy.setDeliveryRadiusKm(request.deliveryRadiusKm());
        pharmacyRepository.save(pharmacy);

        for (int day = 1; day <= 7; day++) {
            PharmacyOperatingHour hours = new PharmacyOperatingHour();
            hours.setPharmacyId(pharmacy.getId());
            hours.setDayOfWeek(day);
            hoursRepository.save(hours);
        }
        auditService.recordForUser(owner.getId(), Role.PHARMACY.name(), "PHARMACY_REGISTERED", "Pharmacy",
                pharmacy.getId(), "Awaiting verification");
        return request;
    }

    @Transactional(readOnly = true)
    public Pharmacy requirePharmacy(Long userId) {
        return pharmacyRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found for this account"));
    }

    @Transactional(readOnly = true)
    public PharmacyDtos.DashboardResponse dashboard(Long userId) {
        Pharmacy pharmacy = requirePharmacy(userId);
        long totalOrders = orderRepository.countByPharmacyId(pharmacy.getId());
        long pending = orderRepository.countByPharmacyIdAndStatusIn(pharmacy.getId(),
                List.of(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.PRESCRIPTION_APPROVED));
        long ready = orderRepository.countByPharmacyIdAndStatusIn(pharmacy.getId(),
                List.of(OrderStatus.READY_FOR_PICKUP));
        long completed = orderRepository.countByPharmacyIdAndStatusIn(pharmacy.getId(),
                List.of(OrderStatus.DELIVERED));
        long cancelled = orderRepository.countByPharmacyIdAndStatusIn(pharmacy.getId(),
                List.of(OrderStatus.CANCELLED, OrderStatus.PRESCRIPTION_REJECTED));
        BigDecimal revenue = orderRepository.sumRevenueForPharmacy(pharmacy.getId(),
                List.of(OrderStatus.DELIVERED));
        long lowStock = inventoryRepository.countLowStock(pharmacy.getId());
        long awaitingRx = prescriptionRepository.findByPharmacyIdAndStatus(pharmacy.getId(),
                PrescriptionStatus.UPLOADED, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements()
                + prescriptionRepository.findByPharmacyIdAndStatus(pharmacy.getId(),
                PrescriptionStatus.UNDER_REVIEW, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();
        return new PharmacyDtos.DashboardResponse(totalOrders, pending, awaitingRx, ready, completed, cancelled,
                revenue, lowStock, inventoryRepository.countByPharmacyIdAndActiveTrue(pharmacy.getId()),
                awaitingRx, pharmacy.getName(), pharmacy.getStatus(), pharmacy.isAcceptingOrders(),
                pharmacy.getRating());
    }

    @Transactional
    public PharmacyDtos.DashboardResponse setAcceptingOrders(Long userId, boolean accepting) {
        Pharmacy pharmacy = requirePharmacy(userId);
        if (pharmacy.getStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.forbidden("Only verified pharmacies can accept orders");
        }
        pharmacy.setAcceptingOrders(accepting);
        pharmacyRepository.save(pharmacy);
        return dashboard(userId);
    }

    @Transactional
    public void adminDecision(Long pharmacyId, boolean approve, String note, Long adminId) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy not found"));
        pharmacy.setStatus(approve ? ApprovalStatus.APPROVED : ApprovalStatus.REJECTED);
        pharmacy.setActive(approve);
        pharmacy.setAcceptingOrders(approve);
        pharmacy.setReviewNote(note);
        pharmacyRepository.save(pharmacy);
        auditService.recordForUser(adminId, Role.ADMIN.name(),
                approve ? "PHARMACY_APPROVED" : "PHARMACY_REJECTED", "Pharmacy", pharmacyId, note);
    }

    @Transactional
    public void suspend(Long pharmacyId, Long adminId, String note) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy not found"));
        pharmacy.setStatus(ApprovalStatus.SUSPENDED);
        pharmacy.setActive(false);
        pharmacy.setAcceptingOrders(false);
        pharmacy.setReviewNote(note);
        pharmacyRepository.save(pharmacy);
        auditService.recordForUser(adminId, Role.ADMIN.name(), "PHARMACY_SUSPENDED", "Pharmacy", pharmacyId, note);
    }
}
