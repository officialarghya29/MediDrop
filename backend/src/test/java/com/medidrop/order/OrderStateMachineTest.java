package com.medidrop.order;

import com.medidrop.common.exception.ApiException;
import com.medidrop.security.Role;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderStateMachineTest {

    @Test
    void customerCannotMarkOrderDelivered() {
        ApiException ex = assertThrows(ApiException.class,
                () -> OrderStateMachine.assertAllowed(OrderStatus.OUT_FOR_DELIVERY,
                        OrderStatus.DELIVERED, Role.CUSTOMER));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void deliveryPartnerCannotApprovePrescription() {
        assertThrows(ApiException.class, () -> OrderStateMachine.assertAllowed(
                OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.PRESCRIPTION_APPROVED, Role.DELIVERY));
        assertThrows(ApiException.class, () -> OrderStateMachine.assertAllowed(
                OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.PRESCRIPTION_APPROVED, Role.CUSTOMER));
    }

    @Test
    void pharmacyCannotSkipPreparation() {
        assertThrows(ApiException.class, () -> OrderStateMachine.assertAllowed(
                OrderStatus.PHARMACY_ACCEPTED, OrderStatus.DELIVERED, Role.PHARMACY));
    }

    @Test
    void validHappyPathTransitionsAreAllowed() {
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(OrderStatus.PAYMENT_CONFIRMED,
                OrderStatus.PHARMACY_ACCEPTED, Role.PHARMACY));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(OrderStatus.PHARMACY_ACCEPTED,
                OrderStatus.PREPARING, Role.PHARMACY));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(OrderStatus.PREPARING,
                OrderStatus.READY_FOR_PICKUP, Role.PHARMACY));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(OrderStatus.READY_FOR_PICKUP,
                OrderStatus.DELIVERY_ASSIGNED, Role.ADMIN));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(OrderStatus.DELIVERY_ASSIGNED,
                OrderStatus.PICKED_UP, Role.DELIVERY));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(OrderStatus.PICKED_UP,
                OrderStatus.OUT_FOR_DELIVERY, Role.DELIVERY));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(OrderStatus.OUT_FOR_DELIVERY,
                OrderStatus.DELIVERED, Role.DELIVERY));
    }

    @Test
    void prescriptionRequiredOrderCannotBeAcceptedBeforeApproval() {
        assertThrows(ApiException.class, () -> OrderStateMachine.assertAllowed(
                OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.PHARMACY_ACCEPTED, Role.PHARMACY));
    }

    @Test
    void deliveredOrderCannotBeCancelled() {
        assertThrows(ApiException.class, () -> OrderStateMachine.assertAllowed(
                OrderStatus.DELIVERED, OrderStatus.CANCELLED, Role.ADMIN));
    }

    @Test
    void refundTerminalStateRequiresRefundPending() {
        assertThrows(ApiException.class, () -> OrderStateMachine.assertAllowed(
                OrderStatus.DELIVERED, OrderStatus.REFUNDED, Role.ADMIN));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(
                OrderStatus.DELIVERED, OrderStatus.REFUND_PENDING, Role.ADMIN));
        assertDoesNotThrow(() -> OrderStateMachine.assertAllowed(
                OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED, Role.ADMIN));
    }

    @Test
    void customerCancellationWindowIsEnforced() {
        assertTrue(OrderStateMachine.customerMayCancel(OrderStatus.PAYMENT_CONFIRMED));
        assertFalse(OrderStateMachine.customerMayCancel(OrderStatus.PICKED_UP));
        assertTrue(OrderStateMachine.customerMayRequestCancellation(OrderStatus.PREPARING));
    }
}
