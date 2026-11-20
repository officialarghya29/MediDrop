package com.medidrop.admin;

import com.medidrop.audit.SystemSetting;
import com.medidrop.common.ApiResponse;
import com.medidrop.common.PageResponse;
import com.medidrop.delivery.DeliveryDtos;
import com.medidrop.delivery.DeliveryService;
import com.medidrop.medicine.MedicineDtos;
import com.medidrop.medicine.MedicineService;
import com.medidrop.order.OrderDtos;
import com.medidrop.order.OrderService;
import com.medidrop.payment.PaymentDtos;
import com.medidrop.payment.PaymentService;
import com.medidrop.pharmacy.PharmacyDtos;
import com.medidrop.pharmacy.PharmacyService;
import com.medidrop.security.CurrentUser;
import com.medidrop.security.Role;
import com.medidrop.support.SupportDtos;
import com.medidrop.support.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final PharmacyService pharmacyService;
    private final DeliveryService deliveryService;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final ReportingService reportingService;
    private final MedicineService medicineService;
    private final SupportService supportService;

    @GetMapping("/dashboard")
    public ApiResponse<AdminDtos.Dashboard> dashboard() {
        return ApiResponse.ok(adminService.dashboard());
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<AdminDtos.UserRow>> users(@RequestParam(required = false) String q,
                                                              @RequestParam(required = false) Role role,
                                                              @RequestParam(defaultValue = "0") int page,
                                                              @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.users(q, role, page, size));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminDtos.UserDetail> user(@PathVariable Long id) {
        return ApiResponse.ok(adminService.userDetail(id));
    }

    @PostMapping("/users/{id}/suspend")
    public ApiResponse<AdminDtos.UserRow> suspendUser(@PathVariable Long id,
                                                      @Valid @RequestBody AdminDtos.SuspendRequest request) {
        return ApiResponse.ok("User updated", adminService.suspend(CurrentUser.id(), id,
                Boolean.TRUE.equals(request.suspend()), request.reason()));
    }

    @GetMapping("/pharmacies")
    public ApiResponse<PageResponse<AdminDtos.PharmacyRow>> pharmacies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.pharmacies(status, q, page, size));
    }

    @PostMapping("/pharmacies/{id}/decision")
    public ApiResponse<Void> decidePharmacy(@PathVariable Long id,
                                            @Valid @RequestBody PharmacyDtos.AdminDecisionRequest request) {
        pharmacyService.adminDecision(id, Boolean.TRUE.equals(request.approve()), request.note(), CurrentUser.id());
        return ApiResponse.ok("Pharmacy decision recorded", null);
    }

    @PostMapping("/pharmacies/{id}/pharmacist-verification")
    public ApiResponse<Void> verifyPharmacist(@PathVariable Long id,
                                              @Valid @RequestBody PharmacyDtos.PharmacistVerificationRequest request) {
        pharmacyService.verifyPharmacist(id, request, CurrentUser.id());
        return ApiResponse.ok("Pharmacist verification recorded", null);
    }

    @PostMapping("/pharmacies/{id}/suspend")
    public ApiResponse<Void> suspendPharmacy(@PathVariable Long id,
                                             @RequestBody(required = false) AdminDtos.SuspendRequest request) {
        pharmacyService.suspend(id, CurrentUser.id(), request == null ? null : request.reason());
        return ApiResponse.ok("Pharmacy suspended", null);
    }

    @GetMapping("/delivery-partners")
    public ApiResponse<PageResponse<AdminDtos.PartnerRow>> partners(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.partners(status, page, size));
    }

    @PostMapping("/delivery-partners/{id}/decision")
    public ApiResponse<Void> decidePartner(@PathVariable Long id,
                                           @Valid @RequestBody DeliveryDtos.DecisionRequest request) {
        deliveryService.adminDecision(id, Boolean.TRUE.equals(request.approve()), request.note(),
                CurrentUser.id());
        return ApiResponse.ok("Delivery partner decision recorded", null);
    }

    @PostMapping("/delivery-partners/{id}/suspend")
    public ApiResponse<Void> suspendPartner(@PathVariable Long id,
                                            @RequestBody(required = false) AdminDtos.SuspendRequest request) {
        deliveryService.suspend(id, CurrentUser.id(), request == null ? null : request.reason());
        return ApiResponse.ok("Delivery partner suspended", null);
    }

    @GetMapping("/orders")
    public ApiResponse<PageResponse<OrderDtos.AdminOrderRow>> orders(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orderService.adminOrders(status, page, size));
    }

    @GetMapping("/orders/{id}")
    public ApiResponse<OrderDtos.OrderDetail> order(@PathVariable Long id) {
        return ApiResponse.ok(orderService.adminDetail(id));
    }

    @PatchMapping("/orders/{id}/status")
    public ApiResponse<OrderDtos.OrderDetail> transition(@PathVariable Long id,
                                                         @Valid @RequestBody OrderDtos.TransitionRequest request) {
        return ApiResponse.ok("Order updated", orderService.adminTransition(CurrentUser.id(), id,
                request.status(), request.reason()));
    }

    @PostMapping("/orders/{id}/refund")
    public ApiResponse<PaymentDtos.RefundResponse> refund(@PathVariable Long id,
                                                          @Valid @RequestBody PaymentDtos.RefundRequest request) {
        if (!id.equals(request.orderId())) {
            throw com.medidrop.common.exception.ApiException.badRequest("Order id in path and body must match");
        }
        var withOrder = new PaymentDtos.RefundRequest(id, request.amount(), request.reason());
        return ApiResponse.ok("Refund processed", paymentService.refund(CurrentUser.id(), withOrder));
    }

    @PostMapping("/orders/{id}/assign-delivery")
    public ApiResponse<Void> assignDelivery(@PathVariable Long id) {
        boolean assigned = deliveryService.autoAssign(id);
        return ApiResponse.ok(assigned ? "Delivery partner assigned"
                : "No eligible delivery partner is online right now", null);
    }

    @GetMapping("/refunds")
    public ApiResponse<List<AdminDtos.RefundRow>> refunds() {
        return ApiResponse.ok(adminService.refunds());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<PageResponse<AdminDtos.AuditRow>> auditLogs(@RequestParam(required = false) String action,
                                                                   @RequestParam(required = false) Long actorId,
                                                                   @RequestParam(defaultValue = "0") int page,
                                                                   @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(adminService.auditLogs(action, actorId, page, size));
    }

    @GetMapping("/reports/platform")
    public ApiResponse<AdminDtos.PlatformReport> platformReport(@RequestParam(defaultValue = "30") int days) {
        return ApiResponse.ok(reportingService.platform(days));
    }

    @GetMapping("/categories")
    public ApiResponse<List<MedicineDtos.CategoryResponse>> categories() {
        return ApiResponse.ok(medicineService.categories());
    }

    @PostMapping("/categories")
    public ApiResponse<AdminDtos.CategoryRequest> createCategory(
            @Valid @RequestBody AdminDtos.CategoryRequest request) {
        return ApiResponse.ok("Category created", adminService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ApiResponse<AdminDtos.CategoryRequest> updateCategory(
            @PathVariable Long id, @Valid @RequestBody AdminDtos.CategoryRequest request) {
        return ApiResponse.ok("Category updated", adminService.updateCategory(id, request));
    }

    @GetMapping("/coupons")
    public ApiResponse<List<AdminDtos.CouponRow>> coupons() {
        return ApiResponse.ok(adminService.coupons());
    }

    @PostMapping("/coupons")
    public ApiResponse<AdminDtos.CouponRow> createCoupon(@Valid @RequestBody AdminDtos.CouponRequest request) {
        return ApiResponse.ok("Coupon created", adminService.createCoupon(request));
    }

    @PutMapping("/coupons/{id}")
    public ApiResponse<AdminDtos.CouponRow> updateCoupon(@PathVariable Long id,
                                                         @Valid @RequestBody AdminDtos.CouponRequest request) {
        return ApiResponse.ok("Coupon updated", adminService.updateCoupon(id, request));
    }

    @DeleteMapping("/coupons/{id}")
    public ApiResponse<Void> deleteCoupon(@PathVariable Long id) {
        adminService.deleteCoupon(id);
        return ApiResponse.ok("Coupon deactivated", null);
    }

    @GetMapping("/settings")
    public ApiResponse<List<SystemSetting>> settings() {
        return ApiResponse.ok(adminService.settings());
    }

    @PutMapping("/settings/{key}")
    public ApiResponse<SystemSetting> updateSetting(@PathVariable String key,
                                                    @Valid @RequestBody AdminDtos.SettingRequest request) {
        return ApiResponse.ok("Setting updated", adminService.updateSetting(CurrentUser.id(), key, request));
    }

    @GetMapping("/support-tickets")
    public ApiResponse<PageResponse<SupportDtos.TicketRow>> tickets(
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(supportService.adminQueue(status, page, size));
    }

    @PostMapping("/support-tickets/{id}/respond")
    public ApiResponse<SupportDtos.TicketRow> respond(@PathVariable Long id,
                                                      @Valid @RequestBody SupportDtos.RespondRequest request) {
        return ApiResponse.ok("Response sent", supportService.respond(CurrentUser.id(), id, request));
    }
}
