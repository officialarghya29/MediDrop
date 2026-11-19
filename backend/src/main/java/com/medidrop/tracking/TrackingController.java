package com.medidrop.tracking;

import com.medidrop.common.ApiResponse;
import com.medidrop.order.OrderDtos;
import com.medidrop.order.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final OrderService orderService;

    @GetMapping("/{key}")
    public ApiResponse<OrderDtos.TrackingResponse> tracking(@PathVariable String key) {
        return ApiResponse.ok(orderService.tracking(key));
    }
}
