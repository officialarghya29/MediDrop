package com.medidrop.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.medidrop.common.Crypto;
import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

@Component
@ConditionalOnProperty(name = "medidrop.payments.provider", havingValue = "razorpay")
@RequiredArgsConstructor
@Slf4j
public class RazorpayPaymentGateway implements PaymentGateway {

    private final MedidropProperties properties;

    private RestClient client() {
        var payments = properties.getPayments();
        if (payments.getRazorpayKeyId().isBlank() || payments.getRazorpayKeySecret().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_NOT_CONFIGURED",
                    "Razorpay credentials are not configured");
        }
        String basic = Base64.getEncoder().encodeToString(
                (payments.getRazorpayKeyId() + ":" + payments.getRazorpayKeySecret())
                        .getBytes(StandardCharsets.UTF_8));
        return RestClient.builder()
                .baseUrl(payments.getRazorpayBaseUrl())
                .defaultHeader("Authorization", "Basic " + basic)
                .build();
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public GatewayOrder createOrder(String receipt, BigDecimal amount) {
        long paise = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
        try {
            JsonNode response = client().post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", paise, "currency", "INR", "receipt", receipt,
                            "payment_capture", 1))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("id")) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                        "Payment provider rejected the order");
            }
            return new GatewayOrder(response.get("id").asText(),
                    properties.getPayments().getRazorpayKeyId(), paise, "INR");
        } catch (ApiException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Razorpay order creation failed: {}", ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                    "Unable to create a payment order right now");
        }
    }

    @Override
    public boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId, String signature) {
        String expected = hmac(providerOrderId + "|" + providerPaymentId,
                properties.getPayments().getRazorpayKeySecret());
        return Crypto.constantTimeEquals(expected, signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        String secret = properties.getPayments().getRazorpayWebhookSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("Rejecting webhook because no webhook secret is configured");
            return false;
        }
        return Crypto.constantTimeEquals(hmac(rawBody, secret), signature);
    }

    @Override
    public void refund(String providerPaymentId, String refundReference, BigDecimal amount) {
        long paise = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
        try {
            client().post()
                    .uri("/payments/{id}/refund", providerPaymentId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("amount", paise, "receipt", refundReference,
                            "notes", Map.of("reference", refundReference)))
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RuntimeException ex) {
            log.error("Razorpay refund failed for {}: {}", providerPaymentId, ex.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED",
                    "The payment provider could not process the refund");
        }
    }

    @Override
    public boolean isTestMode() {
        return properties.getPayments().getRazorpayKeyId().startsWith("rzp_test_");
    }

    private String hmac(String payload, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to compute payment signature", e);
        }
    }
}
