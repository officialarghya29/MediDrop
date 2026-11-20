package com.medidrop.order;

import com.medidrop.common.exception.ApiException;
import com.medidrop.security.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for order lifecycle rules. Every transition is validated here,
 * in addition to per-endpoint authorisation and business checks.
 */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Map<OrderStatus, Set<Role>>> TRANSITIONS = new EnumMap<>(OrderStatus.class);

    private OrderStateMachine() {
    }

    private static void allow(OrderStatus from, OrderStatus to, Role... roles) {
        TRANSITIONS.computeIfAbsent(from, k -> new EnumMap<>(OrderStatus.class))
                .put(to, EnumSet.copyOf(Set.of(roles)));
    }

    static {
        allow(OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, Role.ADMIN);
        allow(OrderStatus.CREATED, OrderStatus.PAYMENT_CONFIRMED, Role.ADMIN);
        allow(OrderStatus.CREATED, OrderStatus.CANCELLED, Role.CUSTOMER, Role.ADMIN);

        allow(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMED, Role.ADMIN);
        allow(OrderStatus.PAYMENT_PENDING, OrderStatus.FAILED, Role.ADMIN);
        allow(OrderStatus.PAYMENT_PENDING, OrderStatus.CANCELLED, Role.CUSTOMER, Role.ADMIN);

        allow(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.PRESCRIPTION_REVIEW_PENDING, Role.ADMIN);
        allow(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.PHARMACY_ACCEPTED, Role.PHARMACY);
        allow(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.REFUND_PENDING, Role.ADMIN);
        allow(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLED, Role.CUSTOMER, Role.ADMIN);
        allow(OrderStatus.PAYMENT_CONFIRMED, OrderStatus.CANCELLATION_REQUESTED, Role.CUSTOMER);

        allow(OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.PRESCRIPTION_APPROVED, Role.PHARMACY);
        allow(OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.PRESCRIPTION_REJECTED, Role.PHARMACY);
        allow(OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.CANCELLED, Role.CUSTOMER, Role.ADMIN);

        allow(OrderStatus.PRESCRIPTION_APPROVED, OrderStatus.PHARMACY_ACCEPTED, Role.PHARMACY);
        allow(OrderStatus.PRESCRIPTION_APPROVED, OrderStatus.CANCELLED, Role.CUSTOMER, Role.ADMIN);
        allow(OrderStatus.PRESCRIPTION_APPROVED, OrderStatus.CANCELLATION_REQUESTED, Role.CUSTOMER);

        allow(OrderStatus.PRESCRIPTION_REJECTED, OrderStatus.CANCELLED, Role.ADMIN);
        allow(OrderStatus.PRESCRIPTION_REJECTED, OrderStatus.REFUND_PENDING, Role.ADMIN);

        allow(OrderStatus.PHARMACY_ACCEPTED, OrderStatus.PREPARING, Role.PHARMACY);
        allow(OrderStatus.PHARMACY_ACCEPTED, OrderStatus.CANCELLED, Role.PHARMACY, Role.ADMIN);
        allow(OrderStatus.PHARMACY_ACCEPTED, OrderStatus.CANCELLATION_REQUESTED, Role.CUSTOMER);

        allow(OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP, Role.PHARMACY);
        allow(OrderStatus.PREPARING, OrderStatus.CANCELLATION_REQUESTED, Role.CUSTOMER);
        allow(OrderStatus.PREPARING, OrderStatus.CANCELLED, Role.ADMIN);

        allow(OrderStatus.READY_FOR_PICKUP, OrderStatus.DELIVERY_ASSIGNED, Role.ADMIN);
        allow(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLATION_REQUESTED, Role.CUSTOMER);
        allow(OrderStatus.READY_FOR_PICKUP, OrderStatus.CANCELLED, Role.ADMIN);

        allow(OrderStatus.DELIVERY_ASSIGNED, OrderStatus.PICKED_UP, Role.DELIVERY);
        allow(OrderStatus.DELIVERY_ASSIGNED, OrderStatus.FAILED, Role.DELIVERY, Role.ADMIN);
        allow(OrderStatus.DELIVERY_ASSIGNED, OrderStatus.CANCELLATION_REQUESTED, Role.CUSTOMER);
        allow(OrderStatus.DELIVERY_ASSIGNED, OrderStatus.CANCELLED, Role.ADMIN);

        allow(OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY, Role.DELIVERY);
        allow(OrderStatus.PICKED_UP, OrderStatus.FAILED, Role.DELIVERY, Role.ADMIN);

        allow(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, Role.DELIVERY);
        allow(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.FAILED, Role.DELIVERY, Role.ADMIN);

        allow(OrderStatus.DELIVERED, OrderStatus.REFUND_PENDING, Role.ADMIN);

        allow(OrderStatus.CANCELLATION_REQUESTED, OrderStatus.CANCELLED, Role.PHARMACY, Role.ADMIN);
        allow(OrderStatus.CANCELLED, OrderStatus.REFUND_PENDING, Role.ADMIN);

        // REFUNDED is reachable only through REFUND_PENDING after a gateway/ledger refund.
        allow(OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED, Role.ADMIN);

        // A failed payment can still have captured funds in a provider edge case; route it
        // through the refund ledger rather than allowing a direct terminal jump.
        allow(OrderStatus.FAILED, OrderStatus.REFUND_PENDING, Role.ADMIN);
    }

    public static void assertAllowed(OrderStatus from, OrderStatus to, Role actor) {
        Set<Role> allowedActors = TRANSITIONS.getOrDefault(from, Map.of()).get(to);
        if (allowedActors == null) {
            throw ApiException.conflict("Cannot move an order from " + from + " to " + to);
        }
        boolean systemDriven = actor == Role.ADMIN && allowedActors.contains(Role.ADMIN);
        if (!allowedActors.contains(actor) && !systemDriven) {
            throw ApiException.forbidden("Your role cannot perform this order transition");
        }
    }

    /**
     * Used by server-side system flows (payment settlement, delivery progress, pharmacist
     * decisions) that have already been authorised by their own service. The transition edge
     * itself is still validated, so no status can be reached out of order.
     */
    public static void assertSystemAllowed(OrderStatus from, OrderStatus to) {
        if (TRANSITIONS.getOrDefault(from, Map.of()).get(to) == null) {
            throw ApiException.conflict("Cannot move an order from " + from + " to " + to);
        }
    }

    public static boolean customerMayCancel(OrderStatus status) {
        return switch (status) {
            case CREATED, PAYMENT_PENDING, PAYMENT_CONFIRMED, PRESCRIPTION_REVIEW_PENDING,
                 PRESCRIPTION_APPROVED, PHARMACY_ACCEPTED -> true;
            default -> false;
        };
    }

    public static boolean customerMayRequestCancellation(OrderStatus status) {
        return switch (status) {
            case PREPARING, READY_FOR_PICKUP, DELIVERY_ASSIGNED -> true;
            default -> false;
        };
    }
}
