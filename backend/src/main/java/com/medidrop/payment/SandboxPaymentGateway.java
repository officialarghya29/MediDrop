package com.medidrop.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medidrop.common.Crypto;
import com.medidrop.config.MedidropProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;

/**
 * Sandbox used only when no Razorpay credentials are configured. Every payment produced here is
 * flagged as TEST in the database and UI. The sandbox refuses to operate under the prod profile.
 */
@Component
@ConditionalOnProperty(name = "medidrop.payments.provider", havingValue = "sandbox", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class SandboxPaymentGateway implements PaymentGateway {

    private final MedidropProperties properties;
    private final Environment environment;
    private final ObjectMapper objectMapper;

    private String secret() {
        if (environment.acceptsProfiles(org.springframework.core.env.Profiles.of("prod", "production"))) {
            throw new IllegalStateException("Sandbox payments are disabled in production. " +
                    "Configure MEDIDROP_PAYMENTS_PROVIDER=razorpay.");
        }
        return properties.getJwt().getSecret();
    }

    @Override
    public String name() {
        return "sandbox";
    }

    @Override
    public GatewayOrder createOrder(String receipt, BigDecimal amount) {
        secret(); // fail fast if sandbox is attempted under a production profile
        String id = "sbx_" + Crypto.randomToken(12);
        long paise = amount.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return new GatewayOrder(id, "sbx_public_key", paise, "INR");
    }

    @Override
    public boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId, String signature) {
        String expected = hmac(providerOrderId + "|" + providerPaymentId, secret());
        return Crypto.constantTimeEquals(expected, signature);
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        return Crypto.constantTimeEquals(hmac(rawBody, properties.getPayments().getRazorpayWebhookSecret().isBlank()
                ? secret() : properties.getPayments().getRazorpayWebhookSecret()), signature);
    }

    /** Test-only helper used by the sandbox completion endpoint. */
    public Map<String, String> simulatePayment(String providerOrderId) {
        String paymentId = "sbxpay_" + Crypto.randomToken(12);
        String signature = hmac(providerOrderId + "|" + paymentId, secret());
        log.info("[SANDBOX] Simulated test payment for {} (no real money moved)", providerOrderId);
        return Map.of("providerPaymentId", paymentId, "signature", signature);
    }

    @Override
    public void refund(String providerPaymentId, String refundReference, BigDecimal amount) {
        secret();
        log.info("[SANDBOX] Recorded test refund {} for payment {} amount {}", refundReference,
                providerPaymentId, amount);
    }

    @Override
    public boolean isTestMode() {
        return true;
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

    @SuppressWarnings("unused")
    private Map<String, Object> parse(String body) {
        try {
            return objectMapper.readValue(body, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }
}
