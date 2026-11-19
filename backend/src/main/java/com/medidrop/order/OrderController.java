package com.medidrop.order;

import com.medidrop.common.ApiResponse;
import com.medidrop.common.PageResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ApiResponse<OrderDtos.OrderDetail> place(
            @Valid @RequestBody OrderDtos.PlaceOrderRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return ApiResponse.ok("Order placed", orderService.place(CurrentUser.id(), request, idempotencyKey));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderDtos.OrderSummary>> list(
            @RequestParam(defaultValue = "all") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ApiResponse.ok(orderService.customerOrders(CurrentUser.id(), filter, page, size));
    }

    @GetMapping("/counts")
    public ApiResponse<OrderDtos.StatusCounts> counts() {
        return ApiResponse.ok(orderService.counts(CurrentUser.id()));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderDtos.OrderDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.customerDetail(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderDtos.OrderDetail> cancel(@PathVariable Long id,
                                                    @Valid @RequestBody(required = false) OrderDtos.CancelRequest request) {
        return ApiResponse.ok("Cancellation submitted",
                orderService.customerCancel(CurrentUser.id(), id, request == null ? null : request.reason()));
    }

    @PostMapping("/{id}/reorder")
    public ApiResponse<Void> reorder(@PathVariable Long id) {
        orderService.reorder(CurrentUser.id(), id);
        return ApiResponse.ok("Available items added to your cart", null);
    }

    @GetMapping("/{id}/invoice")
    public ApiResponse<OrderDtos.InvoiceResponse> invoice(@PathVariable Long id) {
        return ApiResponse.ok(orderService.invoice(CurrentUser.id(), id, false));
    }
}
