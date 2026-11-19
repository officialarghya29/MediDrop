package com.medidrop.order;

import com.medidrop.common.ApiResponse;
import com.medidrop.common.PageResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pharmacy/orders")
@RequiredArgsConstructor
public class PharmacyOrderController {

    private final OrderService orderService;

    @GetMapping
    public ApiResponse<PageResponse<OrderDtos.OrderSummary>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(orderService.pharmacyOrders(CurrentUser.id(), status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDtos.OrderDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.pharmacyDetail(CurrentUser.id(), id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<OrderDtos.OrderDetail> transition(@PathVariable Long id,
                                                         @Valid @RequestBody OrderDtos.TransitionRequest request) {
        return ApiResponse.ok("Order updated", orderService.pharmacyTransition(CurrentUser.id(), id,
                request.status(), request.reason()));
    }

    @GetMapping("/{id}/invoice")
    public ApiResponse<OrderDtos.InvoiceResponse> invoice(@PathVariable Long id) {
        return ApiResponse.ok(orderService.invoice(CurrentUser.id(), id, true));
    }
}
