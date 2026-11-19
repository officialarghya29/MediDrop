package com.medidrop.payment;

import java.math.BigDecimal;

public interface PaymentGateway {

    String name();

    GatewayOrder createOrder(String receipt, BigDecimal amount);

    boolean verifyCheckoutSignature(String providerOrderId, String providerPaymentId, String signature);

    boolean verifyWebhookSignature(String rawBody, String signature);

    void refund(String providerPaymentId, String refundReference, BigDecimal amount);

    boolean isTestMode();

    record GatewayOrder(String id, String publicKey, long amountPaise, String currency) {
    }
}
