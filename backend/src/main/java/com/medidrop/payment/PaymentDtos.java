package com.medidrop.payment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

public final class PaymentDtos {

    private PaymentDtos() {
    }

    public record InitiateRequest(@NotNull Long orderId, @NotNull PaymentMethod method) {
    }

    public record CheckoutSession(Long paymentId, String provider, String providerOrderId, String publicKey,
                                  long amountPaise, String currency, boolean testMode, String status) {
    }

    public record VerifyRequest(@NotBlank String providerOrderId, @NotBlank String providerPaymentId,
                                @NotBlank String signature) {
    }

    public record SandboxCompleteRequest(@NotBlank String providerOrderId) {
    }

    public record PaymentResponse(Long id, Long orderId, String status, String method, BigDecimal amount,
                                  String provider, String providerPaymentId, boolean test, Instant paidAt) {
    }

    public record RefundRequest(@NotNull Long orderId, @DecimalMin("1.00") BigDecimal amount,
                                @NotBlank @Size(max = 300) String reason) {
    }

    public record RefundResponse(Long id, Long orderId, BigDecimal amount, String status, Instant processedAt) {
    }
}
