package com.medidrop.admin;

import com.medidrop.audit.AuditLogRepository;
import com.medidrop.audit.AuditService;
import com.medidrop.audit.SystemSetting;
import com.medidrop.audit.SystemSettingRepository;
import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.PageResponse;
import com.medidrop.common.exception.ApiException;
import com.medidrop.delivery.DeliveryPartner;
import com.medidrop.delivery.DeliveryPartnerDocumentRepository;
import com.medidrop.delivery.DeliveryPartnerRepository;
import com.medidrop.medicine.MedicineCategory;
import com.medidrop.medicine.MedicineCategoryRepository;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderStatus;
import com.medidrop.payment.PaymentRepository;
import com.medidrop.payment.RefundRepository;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyDocumentRepository;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.prescription.PrescriptionRepository;
import com.medidrop.prescription.PrescriptionStatus;
import com.medidrop.promo.Coupon;
import com.medidrop.promo.CouponRepository;
import com.medidrop.security.Role;
import com.medidrop.support.SupportTicketRepository;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyDocumentRepository pharmacyDocumentRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryPartnerDocumentRepository partnerDocumentRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final SupportTicketRepository supportTicketRepository;
    private final CouponRepository couponRepository;
    private final MedicineCategoryRepository categoryRepository;
    private final SystemSettingRepository settingRepository;
    private final AuditLogRepository auditLogRepository;
    private final ReportingService reportingService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public AdminDtos.Dashboard dashboard() {
        return new AdminDtos.Dashboard(
                userRepository.countByRole(Role.CUSTOMER),
                pharmacyRepository.count(),
                pharmacyRepository.countByStatus(ApprovalStatus.PENDING),
                partnerRepository.count(),
                partnerRepository.countByStatus(ApprovalStatus.PENDING),
                orderRepository.count(),
                orderRepository.countByStatus(OrderStatus.PAYMENT_PENDING)
                        + orderRepository.countByStatus(OrderStatus.PAYMENT_CONFIRMED)
                        + orderRepository.countByStatus(OrderStatus.PRESCRIPTION_REVIEW_PENDING)
                        + orderRepository.countByStatus(OrderStatus.PRESCRIPTION_APPROVED)
                        + orderRepository.countByStatus(OrderStatus.PHARMACY_ACCEPTED)
                        + orderRepository.countByStatus(OrderStatus.PREPARING)
                        + orderRepository.countByStatus(OrderStatus.READY_FOR_PICKUP)
                        + orderRepository.countByStatus(OrderStatus.DELIVERY_ASSIGNED)
                        + orderRepository.countByStatus(OrderStatus.PICKED_UP)
                        + orderRepository.countByStatus(OrderStatus.OUT_FOR_DELIVERY),
                orderRepository.countByStatus(OrderStatus.DELIVERED),
                orderRepository.countByStatus(OrderStatus.CANCELLED),
                paymentRepository.sumCaptured(),
                refundRepository.sumProcessed(),
                orderRepository.countSince(Instant.now().minus(Duration.ofDays(1))),
                prescriptionRepository.countByStatus(PrescriptionStatus.UPLOADED)
                        + prescriptionRepository.countByStatus(PrescriptionStatus.UNDER_REVIEW),
                supportTicketRepository.countByStatus("OPEN"),
                reportingService.orderTrend(14));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.UserRow> users(String q, Role role, int page, int size) {
        Page<User> users = userRepository.search(q == null || q.isBlank() ? null : q.trim(), role,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                        Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.of(users, this::userRow);
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserDetail userDetail(Long id) {
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User not found"));
        long orders = orderRepository.countByCustomerId(id);
        return new AdminDtos.UserDetail(userRow(user), orders, user.getLastLoginAt(),
                user.getDeletionRequestedAt());
    }

    @Transactional
    public AdminDtos.UserRow suspend(Long adminId, Long userId, boolean suspend, String reason) {
        User user = userRepository.findById(userId).orElseThrow(() -> ApiException.notFound("User not found"));
        if (user.getRole() == Role.ADMIN) {
            throw ApiException.forbidden("Administrator accounts cannot be suspended here");
        }
        user.setLocked(suspend);
        user.setEnabled(!suspend);
        userRepository.save(user);
        auditService.recordForUser(adminId, Role.ADMIN.name(),
                suspend ? "USER_SUSPENDED" : "USER_REACTIVATED", "User", userId, reason);
        return userRow(user);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.PharmacyRow> pharmacies(String status, String q, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Pharmacy> pharmacies = status == null || status.isBlank()
                ? pharmacyRepository.searchApproved(q, pageable)
                : pharmacyRepository.searchByStatus(parseApprovalStatus(status), q, pageable);
        return PageResponse.of(pharmacies, p -> new AdminDtos.PharmacyRow(p.getId(), p.getName(), p.getCity(),
                p.getLicenseNumber(), p.getPhone(), p.getStatus(), p.getCreatedAt(), p.getRating(),
                pharmacyDocumentRepository.findByPharmacyId(p.getId()).stream()
                        .map(d -> new AdminMisc.DocumentRow(d.getId(), d.getDocumentType(), d.getFileName(),
                                d.getStatus(), d.getCreatedAt()))
                        .toList()));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.PartnerRow> partners(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DeliveryPartner> partners = status == null || status.isBlank()
                ? partnerRepository.findAll(pageable)
                : partnerRepository.findByStatus(parseApprovalStatus(status), pageable);
        return PageResponse.of(partners, p -> new AdminDtos.PartnerRow(p.getId(), p.getFullName(), p.getPhone(),
                p.getVehicleType(), p.getStatus(), p.isOnline(), p.getCompletedDeliveries(), p.getRating(),
                p.getCreatedAt(), partnerDocumentRepository.findByPartnerId(p.getId()).stream()
                .map(d -> new AdminMisc.DocumentRow(d.getId(), d.getDocumentType(), d.getFileName(),
                        d.getStatus(), d.getCreatedAt()))
                .toList()));
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.RefundRow> refunds() {
        return refundRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)).getContent().stream()
                .map(r -> new AdminDtos.RefundRow(r.getId(), r.getOrderId(), r.getAmount(), r.getStatus(),
                        r.getReason(), r.getProcessedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDtos.AuditRow> auditLogs(String action, Long actorId, int page, int size) {
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        String normalizedAction = action == null || action.isBlank() ? null : action.trim();
        var logs = normalizedAction == null
                ? (actorId == null ? auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                : auditLogRepository.findByActorUserIdOrderByCreatedAtDesc(actorId, pageable))
                : (actorId == null
                ? auditLogRepository.findByActionIgnoreCaseOrderByCreatedAtDesc(normalizedAction, pageable)
                : auditLogRepository.findByActionIgnoreCaseAndActorUserIdOrderByCreatedAtDesc(normalizedAction,
                actorId, pageable));
        return PageResponse.of(logs, a -> new AdminDtos.AuditRow(a.getId(), a.getActorRole(), a.getActorUserId(),
                a.getAction(), a.getEntityType(), a.getEntityId(), a.getDetails(), a.getCreatedAt()));
    }

    @Transactional
    public AdminDtos.CategoryRequest createCategory(AdminDtos.CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw ApiException.conflict("Category already exists");
        }
        MedicineCategory category = new MedicineCategory();
        applyCategory(category, request);
        categoryRepository.save(category);
        return request;
    }

    @Transactional
    public AdminDtos.CategoryRequest updateCategory(Long id, AdminDtos.CategoryRequest request) {
        MedicineCategory category = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Category not found"));
        applyCategory(category, request);
        categoryRepository.save(category);
        return request;
    }

    @Transactional
    public AdminDtos.CouponRow createCoupon(AdminDtos.CouponRequest request) {
        couponRepository.findByCodeIgnoreCase(request.code()).ifPresent(c -> {
            throw ApiException.conflict("Coupon code already exists");
        });
        Coupon coupon = new Coupon();
        applyCoupon(coupon, request);
        couponRepository.save(coupon);
        return couponRow(coupon);
    }

    @Transactional
    public AdminDtos.CouponRow updateCoupon(Long id, AdminDtos.CouponRequest request) {
        Coupon coupon = couponRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Coupon not found"));
        applyCoupon(coupon, request);
        couponRepository.save(coupon);
        return couponRow(coupon);
    }

    @Transactional(readOnly = true)
    public List<AdminDtos.CouponRow> coupons() {
        return couponRepository.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream().map(this::couponRow).toList();
    }

    @Transactional(readOnly = true)
    public List<SystemSetting> settings() {
        return settingRepository.findAll();
    }

    @Transactional
    public SystemSetting updateSetting(String key, AdminDtos.SettingRequest request) {
        SystemSetting setting = settingRepository.findById(key).orElseGet(SystemSetting::new);
        setting.setKey(key);
        setting.setValue(request.value());
        setting.setDescription(request.description());
        if (request.publicSetting() != null) setting.setPublicSetting(request.publicSetting());
        settingRepository.save(setting);
        return setting;
    }

    @Transactional
    public void deleteCoupon(Long id) {
        Coupon coupon = couponRepository.findById(id).orElseThrow(() -> ApiException.notFound("Coupon not found"));
        coupon.setActive(false);
        couponRepository.save(coupon);
    }

    private void applyCategory(MedicineCategory category, AdminDtos.CategoryRequest request) {
        category.setName(request.name().trim());
        category.setSlug(request.name().trim().toLowerCase().replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", ""));
        category.setDescription(request.description());
        category.setPrescriptionOnly(request.prescriptionOnly());
        if (request.displayOrder() != null) category.setDisplayOrder(request.displayOrder());
        if (request.active() != null) category.setActive(request.active());
    }

    private void applyCoupon(Coupon coupon, AdminDtos.CouponRequest request) {
        coupon.setCode(request.code().trim().toUpperCase());
        coupon.setType(parseCouponType(request.type()));
        coupon.setValue(request.value());
        if (request.minOrderValue() != null) coupon.setMinOrderValue(request.minOrderValue());
        coupon.setMaxDiscount(request.maxDiscount());
        if (request.active() != null) coupon.setActive(request.active());
        coupon.setUsageLimit(request.usageLimit());
        coupon.setDescription(request.description());
    }

    private ApprovalStatus parseApprovalStatus(String status) {
        try {
            return ApprovalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown approval status: " + status);
        }
    }

    private Coupon.Type parseCouponType(String type) {
        try {
            return Coupon.Type.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.badRequest("Unknown coupon type: " + type);
        }
    }

    private AdminDtos.CouponRow couponRow(Coupon c) {
        return new AdminDtos.CouponRow(c.getId(), c.getCode(), c.getType().name(), c.getValue(),
                c.getMinOrderValue(), c.getMaxDiscount(), c.isActive(), c.getUsageLimit(), c.getUsedCount(),
                c.getValidTo());
    }

    private AdminDtos.UserRow userRow(User u) {
        return new AdminDtos.UserRow(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(),
                u.isEnabled(), u.isLocked(), u.isEmailVerified(), u.getCreatedAt());
    }
}
