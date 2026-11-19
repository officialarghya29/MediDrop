package com.medidrop.delivery;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody DeliveryDtos.RegistrationRequest request) {
        deliveryService.register(request);
        return ApiResponse.ok("Application submitted. You can sign in once an administrator approves it.", null);
    }

    @GetMapping("/profile")
    public ApiResponse<DeliveryDtos.PartnerProfile> profile() {
        return ApiResponse.ok(deliveryService.profile(CurrentUser.id()));
    }

    @GetMapping("/dashboard")
    public ApiResponse<DeliveryDtos.DashboardResponse> dashboard() {
        return ApiResponse.ok(deliveryService.dashboard(CurrentUser.id()));
    }

    @PostMapping("/availability")
    public ApiResponse<DeliveryDtos.PartnerProfile> availability(
            @Valid @RequestBody DeliveryDtos.AvailabilityRequest request) {
        return ApiResponse.ok("Availability updated", deliveryService.setAvailability(CurrentUser.id(), request));
    }

    @GetMapping("/assignments")
    public ApiResponse<List<DeliveryDtos.AssignmentRow>> assignments(
            @RequestParam(required = false) String status) {
        return ApiResponse.ok(deliveryService.assignments(CurrentUser.id(), status));
    }

    @PostMapping("/assignments/{id}/accept")
    public ApiResponse<DeliveryDtos.AssignmentRow> accept(@PathVariable Long id) {
        return ApiResponse.ok("Delivery accepted", deliveryService.accept(CurrentUser.id(), id));
    }

    @PostMapping("/assignments/{id}/reject")
    public ApiResponse<DeliveryDtos.AssignmentRow> reject(@PathVariable Long id,
                                                          @RequestBody(required = false) DeliveryDtos.StatusRequest request) {
        return ApiResponse.ok("Delivery declined",
                deliveryService.reject(CurrentUser.id(), id, request == null ? null : request.note()));
    }

    @PatchMapping("/assignments/{id}/status")
    public ApiResponse<DeliveryDtos.AssignmentRow> status(@PathVariable Long id,
                                                          @Valid @RequestBody DeliveryDtos.StatusRequest request) {
        return ApiResponse.ok("Delivery updated", deliveryService.updateStatus(CurrentUser.id(), id, request));
    }

    @PostMapping("/assignments/{id}/complete")
    public ApiResponse<DeliveryDtos.AssignmentRow> complete(
            @PathVariable Long id, @Valid @RequestBody DeliveryDtos.CompleteRequest request) {
        return ApiResponse.ok("Delivery completed", deliveryService.complete(CurrentUser.id(), id, request));
    }

    @PostMapping("/location")
    public ApiResponse<Void> location(@Valid @RequestBody DeliveryDtos.LocationPing ping) {
        deliveryService.pingLocation(CurrentUser.id(), ping);
        return ApiResponse.ok(null);
    }
}
