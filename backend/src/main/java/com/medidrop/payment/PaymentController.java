package com.medidrop.payment;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public ApiResponse<PaymentDtos.CheckoutSession> create(
            @Valid @RequestBody PaymentDtos.InitiateRequest request) {
        return ApiResponse.ok(paymentService.initiate(CurrentUser.id(), request.orderId(), request.method()));
    }

    @PostMapping("/verify")
    public ApiResponse<PaymentDtos.PaymentResponse> verify(
            @Valid @RequestBody PaymentDtos.VerifyRequest request) {
        return ApiResponse.ok("Payment verified", paymentService.verify(CurrentUser.id(), request));
    }

    @PostMapping("/sandbox/complete")
    public ApiResponse<PaymentDtos.PaymentResponse> sandboxComplete(
            @Valid @RequestBody PaymentDtos.SandboxCompleteRequest request) {
        return ApiResponse.ok("Sandbox payment completed (test only)",
                paymentService.sandboxComplete(CurrentUser.id(), request.providerOrderId()));
    }

    @PostMapping("/webhook")
    public ResponseEntity<Map<String, Object>> webhook(@RequestBody String body,
                                                       @RequestHeader(value = "X-Razorpay-Signature", required = false)
                                                       String signature) {
        paymentService.handleWebhook(body, signature);
        return ResponseEntity.status(HttpStatus.OK).body(Map.of("received", true));
    }

    @GetMapping("/history")
    public ApiResponse<List<PaymentDtos.PaymentResponse>> history() {
        return ApiResponse.ok(paymentService.history(CurrentUser.id()));
    }
}
