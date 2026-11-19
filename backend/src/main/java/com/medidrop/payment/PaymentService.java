package com.medidrop.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medidrop.audit.AuditService;
import com.medidrop.common.Crypto;
import com.medidrop.common.exception.ApiException;
import com.medidrop.notification.NotificationService;
import com.medidrop.order.Order;
import com.medidrop.order.OrderRepository;
import com.medidrop.order.OrderService;
import com.medidrop.order.OrderStatus;
import com.medidrop.prescription.Prescription;
import com.medidrop.prescription.PrescriptionRepository;
import com.medidrop.prescription.PrescriptionStatus;
import com.medidrop.security.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final PrescriptionRepository prescriptionRepository;
    private final PaymentGateway gateway;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public PaymentDtos.CheckoutSession initiate(Long customerId, Long orderId, PaymentMethod method) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (method == PaymentMethod.COD) {
            throw ApiException.badRequest("Cash on delivery does not require an online payment");
        }
        if (order.getStatus() != OrderStatus.PAYMENT_PENDING) {
            throw ApiException.conflict("This order is not awaiting payment");
        }
        PaymentGateway.GatewayOrder gatewayOrder = gateway.createOrder(order.getOrderNumber(), order.getTotalAmount());
        Payment payment = new Payment();
        payment.setOrderId(order.getId());
        payment.setCustomerId(customerId);
        payment.setProvider(gateway.name());
        payment.setProviderOrderId(gatewayOrder.id());
        payment.setClientReference("pay_" + Crypto.randomToken(16));
        payment.setAmount(order.getTotalAmount());
        payment.setCurrency("INR");
        payment.setMethod(method);
        payment.setStatus(PaymentStatus.CREATED);
        payment.setTest(gateway.isTestMode());
        paymentRepository.save(payment);
        auditService.recordForUser(customerId, Role.CUSTOMER.name(), "PAYMENT_INITIATED", "Payment",
                payment.getId(), order.getOrderNumber());
        return new PaymentDtos.CheckoutSession(payment.getId(), gateway.name(), gatewayOrder.id(),
                gatewayOrder.publicKey(), gatewayOrder.amountPaise(), gatewayOrder.currency(),
                gateway.isTestMode(), payment.getStatus().name());
    }

    @Transactional
    public PaymentDtos.PaymentResponse verify(Long customerId, PaymentDtos.VerifyRequest request) {
        Payment payment = paymentRepository.findForUpdateByProviderOrderId(request.providerOrderId())
                .orElseThrow(() -> ApiException.notFound("Payment attempt not found"));
        if (!payment.getCustomerId().equals(customerId)) {
            throw ApiException.forbidden("This payment belongs to another account");
        }
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return toDto(payment);
        }
        if (!gateway.verifyCheckoutSignature(request.providerOrderId(), request.providerPaymentId(),
                request.signature())) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Signature verification failed");
            paymentRepository.save(payment);
            auditService.recordForUser(customerId, Role.CUSTOMER.name(), "PAYMENT_SIGNATURE_INVALID",
                    "Payment", payment.getId(), null);
            throw ApiException.badRequest("Payment could not be verified");
        }
        settle(payment, request.providerPaymentId(), true);
        return toDto(payment);
    }

    /**
     * Razorpay webhook. Verifies the HMAC signature over the raw body before any state change,
     * and is idempotent for repeated deliveries.
     */
    @Transactional
    public void handleWebhook(String rawBody, String signature) {
        if (signature == null || !gateway.verifyWebhookSignature(rawBody, signature)) {
            throw ApiException.unauthorized("Invalid webhook signature");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(rawBody);
        } catch (Exception e) {
            throw ApiException.badRequest("Malformed webhook payload");
        }
        String event = root.path("event").asText("");
        JsonNode entity = root.path("payload").path("payment").path("entity");
        String providerOrderId = entity.path("order_id").asText(null);
        String providerPaymentId = entity.path("id").asText(null);
        if (providerOrderId == null) {
            log.warn("Webhook {} ignored: no order reference", event);
            return;
        }
        Payment payment = paymentRepository.findForUpdateByProviderOrderId(providerOrderId).orElse(null);
        if (payment == null) {
            log.warn("Webhook {} references unknown payment order {}", event, providerOrderId);
            return;
        }
        if ("payment.captured".equals(event)) {
            if (payment.getStatus() == PaymentStatus.CAPTURED) {
                return; // duplicate delivery
            }
            settle(payment, providerPaymentId, true);
        } else if ("payment.failed".equals(event)) {
            if (payment.getStatus() != PaymentStatus.CAPTURED) {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(entity.path("error_description").asText("Payment failed"));
                paymentRepository.save(payment);
                notificationService.notifyUser(payment.getCustomerId(), "PAYMENT", "Payment failed",
                        "Your payment could not be completed. You can retry from the order page.",
                        "/orders/" + payment.getOrderId());
            }
        } else {
            log.info("Unhandled webhook event type {}", event);
        }
    }

    @Transactional
    public PaymentDtos.PaymentResponse sandboxComplete(Long customerId, String providerOrderId) {
        if (!(gateway instanceof SandboxPaymentGateway sandbox)) {
            throw ApiException.forbidden("Sandbox completion is only available with the sandbox provider");
        }
        Payment payment = paymentRepository.findForUpdateByProviderOrderId(providerOrderId)
                .orElseThrow(() -> ApiException.notFound("Payment attempt not found"));
        if (!payment.getCustomerId().equals(customerId)) {
            throw ApiException.forbidden("This payment belongs to another account");
        }
        if (payment.getStatus() == PaymentStatus.CAPTURED) {
            return toDto(payment);
        }
        var simulated = sandbox.simulatePayment(providerOrderId);
        if (!gateway.verifyCheckoutSignature(providerOrderId, simulated.get("providerPaymentId"),
                simulated.get("signature"))) {
            throw ApiException.badRequest("Sandbox signature verification failed");
        }
        settle(payment, simulated.get("providerPaymentId"), true);
        return toDto(payment);
    }

    @Transactional
    public PaymentDtos.RefundResponse refund(Long adminId, PaymentDtos.RefundRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        Payment payment = paymentRepository.findRefundableForUpdate(order.getId()).stream()
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("No captured payment exists for this order"));
        BigDecimal alreadyRefunded = refundRepository.sumProcessedForOrder(order.getId());
        BigDecimal amount = request.amount() == null ? payment.getAmount().subtract(alreadyRefunded)
                : request.amount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0
                || alreadyRefunded.add(amount).compareTo(payment.getAmount()) > 0) {
            throw ApiException.badRequest("Refund amount exceeds the captured amount");
        }
        String reference = "refund_" + Crypto.randomToken(12);
        gateway.refund(payment.getProviderPaymentId(), reference, amount);

        Refund refund = new Refund();
        refund.setPaymentId(payment.getId());
        refund.setOrderId(order.getId());
        refund.setAmount(amount);
        refund.setReason(request.reason());
        refund.setStatus("PROCESSED");
        refund.setProviderRefundId(reference);
        refund.setRequestedBy(adminId);
        refund.setProcessedAt(Instant.now());
        refundRepository.save(refund);

        payment.setStatus(alreadyRefunded.add(amount).compareTo(payment.getAmount()) >= 0
                ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        paymentRepository.save(payment);
        if (payment.getStatus() == PaymentStatus.REFUNDED && order.getStatus() != OrderStatus.REFUNDED) {
            orderService.adminTransition(adminId, order.getId(), OrderStatus.REFUNDED,
                    "Refund " + reference + " processed");
        }
        notificationService.notifyUser(order.getCustomerId(), "PAYMENT", "Refund processed",
                "₹" + amount.toPlainString() + " has been refunded for order " + order.getOrderNumber() + ".",
                "/orders/" + order.getId());
        auditService.recordForUser(adminId, Role.ADMIN.name(), "REFUND_PROCESSED", "Order", order.getId(),
                reference);
        return new PaymentDtos.RefundResponse(refund.getId(), order.getId(), amount, refund.getStatus(),
                refund.getProcessedAt());
    }

    @Transactional(readOnly = true)
    public List<PaymentDtos.PaymentResponse> history(Long customerId) {
        return paymentRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toDto).toList();
    }

    private void settle(Payment payment, String providerPaymentId, boolean signatureVerified) {
        payment.setProviderPaymentId(providerPaymentId);
        payment.setSignatureVerified(signatureVerified);
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCapturedAt(Instant.now());
        paymentRepository.save(payment);

        Order order = orderRepository.findById(payment.getOrderId()).orElse(null);
        if (order == null) return;
        if (order.getStatus() == OrderStatus.PAYMENT_PENDING) {
            orderService.systemTransition(order, OrderStatus.PAYMENT_CONFIRMED, "Payment captured");
            Prescription rx = order.getPrescriptionId() == null ? null
                    : prescriptionRepository.findById(order.getPrescriptionId()).orElse(null);
            if (order.isPrescriptionRequired() && (rx == null || rx.getStatus() != PrescriptionStatus.APPROVED)) {
                orderService.systemTransition(order, OrderStatus.PRESCRIPTION_REVIEW_PENDING,
                        "Awaiting pharmacist review");
            }
        }
        notificationService.notifyUser(payment.getCustomerId(), "PAYMENT", "Payment successful",
                "We received your payment for order " + order.getOrderNumber() + ".",
                "/orders/" + order.getId());
    }

    private PaymentDtos.PaymentResponse toDto(Payment p) {
        return new PaymentDtos.PaymentResponse(p.getId(), p.getOrderId(), p.getStatus().name(),
                p.getMethod() == null ? null : p.getMethod().name(), p.getAmount(), p.getProvider(),
                p.getProviderPaymentId(), p.isTest(), p.getCapturedAt());
    }
}
