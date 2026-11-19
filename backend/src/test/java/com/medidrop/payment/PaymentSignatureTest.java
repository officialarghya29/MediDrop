package com.medidrop.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medidrop.config.MedidropProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentSignatureTest {

    private SandboxPaymentGateway gateway;

    @BeforeEach
    void setUp() {
        MedidropProperties properties = new MedidropProperties();
        properties.getJwt().setSecret("test-secret-key-long-enough-for-hmac-signing-123456");
        MockEnvironment environment = new MockEnvironment();
        gateway = new SandboxPaymentGateway(properties, environment, new ObjectMapper());
    }

    @Test
    void sandboxSignaturesAreVerifiedAndTamperingIsRejected() {
        var order = gateway.createOrder("MD-TEST-1", new BigDecimal("499.00"));
        assertEquals(49900L, order.amountPaise());

        Map<String, String> simulated = gateway.simulatePayment(order.id());
        assertTrue(gateway.verifyCheckoutSignature(order.id(), simulated.get("providerPaymentId"),
                simulated.get("signature")));
        assertFalse(gateway.verifyCheckoutSignature(order.id(), simulated.get("providerPaymentId"),
                "deadbeef"));
        assertFalse(gateway.verifyCheckoutSignature("other-order", simulated.get("providerPaymentId"),
                simulated.get("signature")));
    }

    @Test
    void sandboxIsDisabledUnderProductionProfile() {
        MedidropProperties properties = new MedidropProperties();
        properties.getJwt().setSecret("test-secret-key-long-enough-for-hmac-signing-123456");
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> new SandboxPaymentGateway(properties, prod, new ObjectMapper())
                        .createOrder("MD-1", new BigDecimal("100")));
    }
}
