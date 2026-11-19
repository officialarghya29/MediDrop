package com.medidrop.review;

import com.medidrop.common.ApiResponse;
import com.medidrop.order.OrderDtos;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping("/{id}/review")
    public ApiResponse<Void> review(@PathVariable Long id,
                                    @Valid @RequestBody OrderDtos.ReviewRequest request) {
        reviewService.submit(CurrentUser.id(), id, request.rating(), request.comment());
        return ApiResponse.ok("Thanks for your feedback", null);
    }
}
