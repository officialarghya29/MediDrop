package com.medidrop.order;

import com.medidrop.audit.AuditService;
import com.medidrop.cart.CartService;
import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.Crypto;
import com.medidrop.common.Haversine;
import com.medidrop.common.PageResponse;
import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import com.medidrop.delivery.DeliveryAssignment;
import com.medidrop.delivery.DeliveryAssignmentRepository;
import com.medidrop.delivery.DeliveryLocationRepository;
import com.medidrop.delivery.DeliveryPartner;
import com.medidrop.delivery.DeliveryPartnerRepository;
import com.medidrop.inventory.InventoryTransaction;
import com.medidrop.inventory.InventoryTransactionRepository;
import com.medidrop.inventory.PharmacyInventory;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.medicine.Medicine;
import com.medidrop.medicine.MedicineRepository;
import com.medidrop.maps.MapsService;
import com.medidrop.notification.NotificationService;
import com.medidrop.payment.PaymentMethod;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.prescription.Prescription;
import com.medidrop.prescription.PrescriptionRepository;
import com.medidrop.prescription.PrescriptionStatus;
import com.medidrop.pricing.PricingService;
import com.medidrop.promo.Coupon;
import com.medidrop.promo.CouponRepository;
import com.medidrop.security.Role;
import com.medidrop.user.Address;
import com.medidrop.user.AddressRepository;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final AddressRepository addressRepository;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final MedicineRepository medicineRepository;
    private final PrescriptionRepository prescriptionRepository;
    private final CouponRepository couponRepository;
    private final CartService cartService;
    private final PricingService pricingService;
    private final MapsService mapsService;
    private final NotificationService notificationService;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryLocationRepository locationRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final MedidropProperties properties;

    private static final Set<OrderStatus> ACTIVE_STATUSES = EnumSet.of(
            OrderStatus.CREATED, OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMED,
            OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.PRESCRIPTION_APPROVED,
            OrderStatus.PHARMACY_ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY_FOR_PICKUP,
            OrderStatus.DELIVERY_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY,
            OrderStatus.CANCELLATION_REQUESTED);

    private static final Set<OrderStatus> CANCELLED_STATUSES = EnumSet.of(
            OrderStatus.CANCELLED, OrderStatus.PRESCRIPTION_REJECTED, OrderStatus.FAILED,
            OrderStatus.REFUNDED);

    private static final Set<OrderStatus> OTP_VISIBLE_STATUSES = EnumSet.of(
            OrderStatus.DELIVERY_ASSIGNED, OrderStatus.PICKED_UP, OrderStatus.OUT_FOR_DELIVERY);

    @Transactional
    public OrderDtos.OrderDetail place(Long customerId, OrderDtos.PlaceOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                if (!existing.get().getCustomerId().equals(customerId)) {
                    throw ApiException.forbidden("Duplicate request key belongs to another account");
                }
                return customerDetail(customerId, existing.get().getId());
            }
        }
        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> ApiException.unauthorized("Account not found"));
        if (!customer.isEmailVerified() && !customer.isPhoneVerified()) {
            throw ApiException.forbidden("Verify your email or mobile number before placing an order");
        }
        Address address = addressRepository.findByIdAndUserId(request.addressId(), customerId)
                .orElseThrow(() -> ApiException.badRequest("Select a valid delivery address"));

        CartService.CheckoutSnapshot snapshot = cartService.checkoutSnapshot(customerId);
        Pharmacy pharmacy = pharmacyRepository.findById(snapshot.pharmacyId())
                .orElseThrow(() -> ApiException.badRequest("Pharmacy unavailable"));
        if (pharmacy.getStatus() != ApprovalStatus.APPROVED || !pharmacy.isActive()) {
            throw ApiException.conflict("This pharmacy is not available for orders");
        }
        if (!pharmacy.isAcceptingOrders()) {
            throw ApiException.conflict("This pharmacy has paused new orders");
        }
        double distance = distanceKm(pharmacy.getLatitude(), pharmacy.getLongitude(),
                address.getLatitude(), address.getLongitude());
        if (distance > pharmacy.getDeliveryRadiusKm()) {
            throw ApiException.conflict("Your address is outside this pharmacy's delivery area");
        }

        boolean rxRequired = snapshot.lines().stream().anyMatch(CartService.SnapshotLine::prescriptionRequired);
        Prescription prescription = null;
        if (rxRequired) {
            if (request.prescriptionId() == null) {
                throw ApiException.badRequest("This order contains prescription-only medicines. " +
                        "Attach an approved prescription.");
            }
            prescription = prescriptionRepository.findByIdAndCustomerId(request.prescriptionId(), customerId)
                    .orElseThrow(() -> ApiException.badRequest("Prescription not found"));
            if (prescription.getPharmacyId() != null && !prescription.getPharmacyId().equals(pharmacy.getId())) {
                throw ApiException.conflict("The selected prescription is assigned to another pharmacy");
            }
            if (prescription.getStatus() == PrescriptionStatus.REJECTED) {
                throw ApiException.conflict("This prescription was rejected. Upload a valid prescription.");
            }
        }

        Coupon coupon = null;
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            // Lock before reading usage so a single-use coupon cannot be redeemed twice concurrently.
            coupon = couponRepository.findForUpdateByCode(request.couponCode().trim())
                    .orElseThrow(() -> ApiException.badRequest("Invalid coupon code"));
        }
        var totals = pricingService.totals(snapshot.subtotal(), coupon, distance);

        // Lock inventory rows in a deterministic order to prevent overselling and deadlocks.
        List<CartService.SnapshotLine> ordered = snapshot.lines().stream()
                .sorted(Comparator.comparing(CartService.SnapshotLine::inventoryId)).toList();
        Map<Long, PharmacyInventory> locked = new LinkedHashMap<>();
        for (CartService.SnapshotLine line : ordered) {
            PharmacyInventory inventory = inventoryRepository.findForUpdate(line.inventoryId())
                    .orElseThrow(() -> ApiException.conflict("An item in your cart is no longer listed"));
            if (!inventory.isActive() || inventory.availableQuantity() < line.quantity()) {
                throw ApiException.conflict("Insufficient stock for " + line.medicineName()
                        + ". Available: " + Math.max(0, inventory.availableQuantity()));
            }
            locked.put(line.inventoryId(), inventory);
        }

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setCustomerId(customerId);
        order.setPharmacyId(pharmacy.getId());
        order.setRecipientName(address.getRecipientName());
        order.setRecipientPhone(address.getPhone());
        order.setLine1(address.getLine1());
        order.setLine2(address.getLine2());
        order.setCity(address.getCity());
        order.setState(address.getState());
        order.setPincode(address.getPincode());
        order.setLatitude(address.getLatitude());
        order.setLongitude(address.getLongitude());
        order.setSubtotal(totals.subtotal());
        order.setDiscount(totals.discount());
        order.setDeliveryFee(totals.deliveryFee());
        order.setTaxAmount(totals.taxAmount());
        order.setTotalAmount(totals.totalAmount());
        order.setPaymentMethod(request.paymentMethod().name());
        order.setPrescriptionRequired(rxRequired);
        order.setPrescriptionId(prescription == null ? null : prescription.getId());
        order.setCouponCode(coupon == null ? null : coupon.getCode());
        order.setCustomerNote(request.note());
        order.setDistanceKm(Math.round(distance * 10.0) / 10.0);
        order.setIdempotencyKey(idempotencyKey);
        order.setTrackingKey(Crypto.randomToken(16));
        order.setCancelEligibleUntil(Instant.now()
                .plusSeconds(properties.getCommerce().getCancelWindowMinutes() * 60L));
        boolean prepaid = request.paymentMethod() != PaymentMethod.COD;
        boolean rxPending = rxRequired && prescription.getStatus() != PrescriptionStatus.APPROVED;
        order.setStatus(prepaid ? OrderStatus.PAYMENT_PENDING
                : rxPending ? OrderStatus.PRESCRIPTION_REVIEW_PENDING : OrderStatus.PAYMENT_CONFIRMED);
        orderRepository.save(order);

        for (CartService.SnapshotLine line : snapshot.lines()) {
            PharmacyInventory inventory = locked.get(line.inventoryId());
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setMedicineId(line.medicineId());
            item.setMedicineName(line.medicineName());
            item.setInventoryId(inventory.getId());
            item.setUnitPrice(inventory.getPrice());
            item.setQuantity(line.quantity());
            item.setLineTotal(inventory.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
            item.setPrescriptionRequired(line.prescriptionRequired());
            orderItemRepository.save(item);

            inventory.setStockQuantity(inventory.getStockQuantity() - line.quantity());
            inventoryRepository.save(inventory);
            InventoryTransaction tx = new InventoryTransaction();
            tx.setInventoryId(inventory.getId());
            tx.setType(InventoryTransaction.Type.SALE);
            tx.setQuantityDelta(-line.quantity());
            tx.setBalanceAfter(inventory.getStockQuantity());
            tx.setReason("Order " + order.getOrderNumber());
            tx.setActorUserId(customerId);
            tx.setOrderId(order.getId());
            transactionRepository.save(tx);
        }

        recordHistory(order, null, order.getStatus(), customerId, Role.CUSTOMER, "Order placed");

        if (prescription != null) {
            prescription.setOrderId(order.getId());
            prescription.setPharmacyId(pharmacy.getId());
            prescriptionRepository.save(prescription);
        }
        if (coupon != null) {
            coupon.setUsedCount(coupon.getUsedCount() + 1);
            couponRepository.save(coupon);
        }
        cartService.clearFor(customerId);

        notificationService.notifyUser(pharmacy.getOwnerUserId(), "ORDER", "New order " + order.getOrderNumber(),
                "A new order has been placed. Review it in your pharmacy dashboard.", "/pharmacy/orders");
        notificationService.notifyUser(customerId, "ORDER", "Order placed",
                "Order " + order.getOrderNumber() + " has been placed with " + pharmacy.getName() + ".",
                "/orders/" + order.getId());
        auditService.recordForUser(customerId, Role.CUSTOMER.name(), "ORDER_PLACED", "Order", order.getId(),
                order.getOrderNumber());
        return customerDetail(customerId, order.getId());
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.OrderSummary> customerOrders(Long customerId, String filter, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders;
        if (filter == null || filter.isBlank() || "all".equalsIgnoreCase(filter)) {
            orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        } else if ("active".equalsIgnoreCase(filter)) {
            orders = orderRepository.findByCustomerIdAndStatusIn(customerId, ACTIVE_STATUSES, pageable);
        } else if ("delivered".equalsIgnoreCase(filter) || "completed".equalsIgnoreCase(filter)) {
            orders = orderRepository.findByCustomerIdAndStatusIn(customerId,
                    List.of(OrderStatus.DELIVERED), pageable);
        } else if ("cancelled".equalsIgnoreCase(filter)) {
            orders = orderRepository.findByCustomerIdAndStatusIn(customerId, CANCELLED_STATUSES, pageable);
        } else {
            throw ApiException.badRequest("Unsupported order filter: " + filter);
        }
        Map<Long, Pharmacy> pharmacies = pharmacyMap(orders.getContent());
        Map<Long, Long> itemCounts = itemCounts(orders.getContent());
        return PageResponse.of(orders, o -> summary(o, pharmacies.get(o.getPharmacyId()),
                itemCounts.getOrDefault(o.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail customerDetail(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return detail(order, true);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.OrderSummary> pharmacyOrders(Long userId, String status, int page, int size) {
        Pharmacy pharmacy = pharmacyRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found"));
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 50),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status))
                ? orderRepository.findByPharmacyIdOrderByCreatedAtDesc(pharmacy.getId(), pageable)
                : orderRepository.findByPharmacyIdAndStatusOrderByCreatedAtDesc(pharmacy.getId(),
                parseStatus(status), pageable);
        Map<Long, Long> itemCounts = itemCounts(orders.getContent());
        return PageResponse.of(orders, o -> summary(o, pharmacy,
                itemCounts.getOrDefault(o.getId(), 0L).intValue()));
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail pharmacyDetail(Long userId, Long orderId) {
        Pharmacy pharmacy = pharmacyRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found"));
        Order order = orderRepository.findByIdAndPharmacyId(orderId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return detail(order, false);
    }

    @Transactional
    public OrderDtos.OrderDetail pharmacyTransition(Long userId, Long orderId, OrderStatus target, String reason) {
        Pharmacy pharmacy = pharmacyRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found"));
        Order order = orderRepository.findByIdAndPharmacyId(orderId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (order.isPrescriptionRequired() && target == OrderStatus.PHARMACY_ACCEPTED) {
            Prescription rx = order.getPrescriptionId() == null ? null
                    : prescriptionRepository.findById(order.getPrescriptionId()).orElse(null);
            if (rx == null || rx.getStatus() != PrescriptionStatus.APPROVED) {
                throw ApiException.forbidden("A pharmacist must approve the prescription before dispensing");
            }
        }
        transition(order, target, Role.PHARMACY, userId, reason);
        notifyCustomer(order, target);
        return detail(order, false);
    }

    @Transactional
    public OrderDtos.OrderDetail customerCancel(Long customerId, Long orderId, String reason) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        if (customerMayCancelNow(order)) {
            transition(order, OrderStatus.CANCELLED, Role.CUSTOMER, customerId, reason);
            notifyPharmacy(order, "Order " + order.getOrderNumber() + " was cancelled by the customer");
        } else if (OrderStateMachine.customerMayRequestCancellation(order.getStatus())) {
            transition(order, OrderStatus.CANCELLATION_REQUESTED, Role.CUSTOMER, customerId, reason);
            notifyPharmacy(order, "Cancellation requested for " + order.getOrderNumber());
        } else {
            throw ApiException.conflict("This order can no longer be cancelled online. Please contact support.");
        }
        return customerDetail(customerId, orderId);
    }

    @Transactional
    public OrderDtos.OrderDetail adminTransition(Long adminId, Long orderId, OrderStatus target, String reason) {
        if (target == OrderStatus.REFUND_PENDING || target == OrderStatus.REFUNDED) {
            throw ApiException.conflict("Use the refund endpoint; refund states require a payment/ledger record");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        transition(order, target, Role.ADMIN, adminId, reason);
        notifyCustomer(order, target);
        return detail(order, false);
    }

    @Transactional
    public OrderDtos.OrderDetail deliveryFailure(Long deliveryUserId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        transition(order, OrderStatus.FAILED, Role.DELIVERY, deliveryUserId, reason);
        notifyCustomer(order, OrderStatus.FAILED);
        return detail(order, false);
    }

    @Transactional
    public OrderDtos.OrderDetail systemTransition(Order order, OrderStatus target, String reason) {
        if (target == OrderStatus.DELIVERED && !order.isDeliveryOtpVerified()) {
            throw ApiException.forbidden("Delivery cannot be completed without OTP verification");
        }
        OrderStateMachine.assertSystemAllowed(order.getStatus(), target);
        applyTransition(order, target, null, null, reason);
        notifyCustomer(order, target);
        return detail(order, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderDtos.AdminOrderRow> adminOrders(String status, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = (status == null || status.isBlank())
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status), pageable);
        Map<Long, Pharmacy> pharmacies = pharmacyMap(orders.getContent());
        Map<Long, User> customers = userRepository.findAllById(
                        orders.getContent().stream().map(Order::getCustomerId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        return PageResponse.of(orders, o -> new OrderDtos.AdminOrderRow(o.getId(), o.getOrderNumber(),
                customers.containsKey(o.getCustomerId()) ? customers.get(o.getCustomerId()).getFullName() : "-",
                pharmacies.containsKey(o.getPharmacyId()) ? pharmacies.get(o.getPharmacyId()).getName() : "-",
                o.getStatus().name(), o.getTotalAmount(), o.getPlacedAt(), Role.CUSTOMER));
    }

    @Transactional(readOnly = true)
    public OrderDtos.OrderDetail adminDetail(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return detail(order, false);
    }

    @Transactional(readOnly = true)
    public OrderDtos.TrackingResponse tracking(String trackingKey) {
        Order order = orderRepository.findByTrackingKey(trackingKey)
                .orElseThrow(() -> ApiException.notFound("Tracking link is invalid"));
        DeliveryAssignment assignment = assignmentRepository.findByOrderId(order.getId()).orElse(null);
        OrderDtos.PartnerInfo partner = null;
        Double lat = null, lng = null;
        Instant updatedAt = null;
        if (assignment != null) {
            DeliveryPartner deliveryPartner = partnerRepository.findById(assignment.getPartnerId()).orElse(null);
            if (deliveryPartner != null) {
                partner = new OrderDtos.PartnerInfo(deliveryPartner.getId(), deliveryPartner.getFullName(),
                        maskPhone(deliveryPartner.getPhone()), deliveryPartner.getCurrentLatitude(),
                        deliveryPartner.getCurrentLongitude(), deliveryPartner.getLastLocationAt(),
                        deliveryPartner.getVehicleType());
                lat = deliveryPartner.getCurrentLatitude();
                lng = deliveryPartner.getCurrentLongitude();
                updatedAt = deliveryPartner.getLastLocationAt();
            }
        }
        int eta = assignment != null && assignment.getEstimatedMinutes() != null
                ? assignment.getEstimatedMinutes() : 0;
        // A tracking link is shareable, so expose only the city and a neighbourhood-level
        // coordinate. The exact street address and doorstep position must never be public.
        Double destinationLat = Math.round(order.getLatitude() * 1000.0) / 1000.0;
        Double destinationLng = Math.round(order.getLongitude() * 1000.0) / 1000.0;
        return new OrderDtos.TrackingResponse(order.getOrderNumber(), order.getStatus().name(),
                order.getTrackingKey(), order.getUpdatedAt(), partner, lat, lng, updatedAt, eta,
                "Delivery area, " + order.getCity(), destinationLat, destinationLng,
                null, null);
    }

    @Transactional
    public void reorder(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        int added = 0;
        for (OrderItem item : items) {
            PharmacyInventory inventory = inventoryRepository
                    .findByPharmacyIdAndMedicineId(order.getPharmacyId(), item.getMedicineId())
                    .filter(PharmacyInventory::isActive)
                    .filter(i -> i.availableQuantity() > 0)
                    .orElse(null);
            if (inventory == null) continue;
            cartService.addItem(customerId, inventory.getId(), Math.min(item.getQuantity(),
                    inventory.availableQuantity()));
            added++;
        }
        if (added == 0) {
            throw ApiException.conflict("None of these items are currently in stock");
        }
    }

    @Transactional(readOnly = true)
    public OrderDtos.StatusCounts counts(Long customerId) {
        long total = orderRepository.countByCustomerId(customerId);
        long active = orderRepository.countByCustomerIdAndStatusIn(customerId, ACTIVE_STATUSES);
        long delivered = orderRepository.countByCustomerIdAndStatusIn(customerId,
                List.of(OrderStatus.DELIVERED));
        long cancelled = orderRepository.countByCustomerIdAndStatusIn(customerId, CANCELLED_STATUSES);
        return new OrderDtos.StatusCounts(total, active, delivered, cancelled);
    }

    @Transactional(readOnly = true)
    public OrderDtos.InvoiceResponse customerInvoice(Long customerId, Long orderId) {
        Order order = orderRepository.findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return invoiceResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderDtos.InvoiceResponse pharmacyInvoice(Long userId, Long orderId) {
        Pharmacy pharmacy = pharmacyRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found"));
        Order order = orderRepository.findByIdAndPharmacyId(orderId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Order not found"));
        return invoiceResponse(order);
    }

    private OrderDtos.InvoiceResponse invoiceResponse(Order order) {
        Pharmacy pharmacy = pharmacyRepository.findById(order.getPharmacyId()).orElse(null);
        List<OrderDtos.Item> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(this::itemDto).toList();
        return new OrderDtos.InvoiceResponse(order.getOrderNumber(), order.getPlacedAt(),
                pharmacy == null ? "MediDrop partner pharmacy" : pharmacy.getName(),
                pharmacy == null ? "" : pharmacy.getRegisteredAddress() + ", " + pharmacy.getCity(),
                pharmacy == null ? null : pharmacy.getGstin(),
                pharmacy == null ? null : pharmacy.getLicenseNumber(),
                order.getRecipientName(), order.getLine1() + ", " + order.getCity() + " " + order.getPincode(),
                items, order.getSubtotal(), order.getDiscount(), order.getTaxAmount(), order.getDeliveryFee(),
                order.getTotalAmount(), order.getPaymentMethod(), null,
                "This is a computer-generated invoice issued by the partner pharmacy.");
    }

    private boolean customerMayCancelNow(Order order) {
        if (!OrderStateMachine.customerMayCancel(order.getStatus())) return false;
        if (order.getCancelEligibleUntil() == null) return true;
        return Instant.now().isBefore(order.getCancelEligibleUntil())
                || List.of(OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_CONFIRMED,
                OrderStatus.PRESCRIPTION_REVIEW_PENDING, OrderStatus.PRESCRIPTION_APPROVED)
                .contains(order.getStatus());
    }

    void transition(Order order, OrderStatus target, Role actor, Long actorId, String reason) {
        OrderStateMachine.assertAllowed(order.getStatus(), target, actor);
        applyTransition(order, target, actor, actorId, reason);
    }

    private void applyTransition(Order order, OrderStatus target, Role actor, Long actorId, String reason) {
        OrderStatus from = order.getStatus();
        if (target == OrderStatus.CANCELLED || target == OrderStatus.PRESCRIPTION_REJECTED
                || target == OrderStatus.FAILED) {
            releaseOrderResources(order, actorId, reason);
        }
        order.setStatus(target);
        if (target == OrderStatus.DELIVERED) {
            order.setDeliveredAt(Instant.now());
        }
        if (target == OrderStatus.CANCELLED) {
            order.setCancellationReason(reason);
        }
        orderRepository.save(order);
        recordHistory(order, from, target, actorId, actor, reason);
        if (actor != null) {
            auditService.recordForUser(actorId, actor.name(), "ORDER_" + target.name(), "Order", order.getId(),
                    reason);
        }
    }

    private void recordHistory(Order order, OrderStatus from, OrderStatus to, Long actorId, Role role, String reason) {
        OrderStatusHistory history = new OrderStatusHistory();
        history.setOrderId(order.getId());
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setActorUserId(actorId);
        history.setActorRole(role);
        history.setReason(reason);
        historyRepository.save(history);
    }

    private void releaseOrderResources(Order order, Long actorId, String reason) {
        if (!order.isCouponReleased() && order.getCouponCode() != null) {
            couponRepository.findForUpdateByCode(order.getCouponCode()).ifPresent(coupon -> {
                int used = coupon.getUsedCount() == null ? 0 : coupon.getUsedCount();
                coupon.setUsedCount(Math.max(0, used - 1));
                couponRepository.save(coupon);
            });
            order.setCouponReleased(true);
        }
        restoreStock(order, actorId, reason == null || reason.isBlank()
                ? "Order " + order.getOrderNumber() + " closed without delivery"
                : reason);
    }

    private void restoreStock(Order order, Long actorId, String reason) {
        if (order.isStockRestored()) return;
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .sorted(Comparator.comparing(OrderItem::getInventoryId)).toList();
        for (OrderItem item : items) {
            // Lock inventory before crediting so a concurrent cancel cannot lose the update.
            inventoryRepository.findForUpdate(item.getInventoryId()).ifPresent(inventory -> {
                inventory.setStockQuantity(inventory.getStockQuantity() + item.getQuantity());
                inventoryRepository.save(inventory);
                InventoryTransaction tx = new InventoryTransaction();
                tx.setInventoryId(inventory.getId());
                tx.setType(InventoryTransaction.Type.RETURN);
                tx.setQuantityDelta(item.getQuantity());
                tx.setBalanceAfter(inventory.getStockQuantity());
                tx.setReason(reason);
                tx.setActorUserId(actorId);
                tx.setOrderId(order.getId());
                transactionRepository.save(tx);
            });
        }
        order.setStockRestored(true);
    }

    private void notifyCustomer(Order order, OrderStatus status) {
        String body = switch (status) {
            case PRESCRIPTION_APPROVED -> "Your prescription was approved. Order " + order.getOrderNumber()
                    + " is being prepared.";
            case PRESCRIPTION_REJECTED -> "Your prescription could not be approved. Please check the order for details.";
            case PHARMACY_ACCEPTED -> "Your order " + order.getOrderNumber() + " was accepted by the pharmacy.";
            case PREPARING -> "Your order " + order.getOrderNumber() + " is being prepared.";
            case READY_FOR_PICKUP -> "Your order " + order.getOrderNumber() + " is packed and awaiting a delivery partner.";
            case DELIVERY_ASSIGNED -> "A delivery partner has been assigned to order " + order.getOrderNumber() + ".";
            case PICKED_UP -> "Your order " + order.getOrderNumber() + " has been picked up.";
            case OUT_FOR_DELIVERY -> "Your order " + order.getOrderNumber() + " is on the way.";
            case DELIVERED -> "Order " + order.getOrderNumber() + " was delivered. Thank you for choosing MediDrop.";
            case CANCELLED -> "Order " + order.getOrderNumber() + " was cancelled.";
            case REFUND_PENDING -> "A refund for order " + order.getOrderNumber() + " is being processed.";
            case REFUNDED -> "Your refund for order " + order.getOrderNumber() + " has been processed.";
            default -> "Order " + order.getOrderNumber() + " status changed to " + status.name();
        };
        notificationService.notifyUser(order.getCustomerId(), "ORDER", "Order update", body,
                "/orders/" + order.getId());
    }

    private void notifyPharmacy(Order order, String message) {
        pharmacyRepository.findById(order.getPharmacyId()).ifPresent(p ->
                notificationService.notifyUser(p.getOwnerUserId(), "ORDER", "Order update", message,
                        "/pharmacy/orders"));
    }

    private OrderDtos.OrderDetail detail(Order order, boolean includeOtp) {
        Pharmacy pharmacy = pharmacyRepository.findById(order.getPharmacyId()).orElse(null);
        User customer = userRepository.findById(order.getCustomerId()).orElse(null);
        List<OrderDtos.Item> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(this::itemDto).toList();
        List<OrderDtos.HistoryEntry> history = historyRepository.findByOrderIdOrderByCreatedAtAsc(order.getId())
                .stream()
                .map(h -> new OrderDtos.HistoryEntry(
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(), h.getActorRole() == null ? null : h.getActorRole().name(),
                        h.getReason(), h.getCreatedAt()))
                .toList();
        DeliveryAssignment assignment = assignmentRepository.findByOrderId(order.getId()).orElse(null);
        OrderDtos.PartnerInfo partner = null;
        int eta = 0;
        if (assignment != null) {
            DeliveryPartner p = partnerRepository.findById(assignment.getPartnerId()).orElse(null);
            if (p != null) {
                partner = new OrderDtos.PartnerInfo(p.getId(), p.getFullName(), maskPhone(p.getPhone()),
                        p.getCurrentLatitude(), p.getCurrentLongitude(), p.getLastLocationAt(), p.getVehicleType());
            }
            if (assignment.getEstimatedMinutes() != null) eta = assignment.getEstimatedMinutes();
        }
        Prescription rx = order.getPrescriptionId() == null ? null
                : prescriptionRepository.findById(order.getPrescriptionId()).orElse(null);
        String addressLine = order.getLine1() + (order.getLine2() == null ? "" : ", " + order.getLine2());
        return new OrderDtos.OrderDetail(order.getId(), order.getOrderNumber(), order.getStatus().name(),
                order.getPharmacyId(), pharmacy == null ? null : pharmacy.getName(),
                pharmacy == null ? null : pharmacy.getPhone(),
                customer == null ? null : customer.getFullName(),
                includeOtp && customer != null ? customer.getPhone() : maskPhone(order.getRecipientPhone()),
                addressLine, order.getCity(), order.getState(), order.getPincode(),
                order.getLatitude(), order.getLongitude(),
                pharmacy == null ? null : pharmacy.getLatitude(), pharmacy == null ? null : pharmacy.getLongitude(),
                order.getSubtotal(), order.getDiscount(), order.getDeliveryFee(), order.getTaxAmount(),
                order.getTotalAmount(), order.getPaymentMethod(), order.isPrescriptionRequired(),
                order.getPrescriptionId(), rx == null ? null : rx.getStatus().name(), order.getCouponCode(),
                order.getCustomerNote(), order.getCancellationReason(), order.getPlacedAt(), order.getDeliveredAt(),
                order.getDistanceKm(), eta, items, history, partner, order.getTrackingKey(),
                includeOtp && !order.isDeliveryOtpVerified() && OTP_VISIBLE_STATUSES.contains(order.getStatus())
                        ? order.getDeliveryOtp() : null,
                order.isDeliveryOtpVerified());
    }

    private OrderDtos.Item itemDto(OrderItem item) {
        return new OrderDtos.Item(item.getId(), item.getMedicineId(), item.getMedicineName(),
                item.getUnitPrice(), item.getQuantity(), item.getLineTotal(), item.isPrescriptionRequired());
    }

    private OrderDtos.OrderSummary summary(Order order, Pharmacy pharmacy, int itemCount) {
        return new OrderDtos.OrderSummary(order.getId(), order.getOrderNumber(), order.getStatus().name(),
                pharmacy == null ? null : pharmacy.getName(), order.getTotalAmount(), itemCount,
                order.getPlacedAt(), order.getPaymentMethod(), order.isPrescriptionRequired(),
                order.getCity(), null);
    }

    private Map<Long, Pharmacy> pharmacyMap(List<Order> orders) {
        if (orders.isEmpty()) return Map.of();
        return pharmacyRepository.findAllById(orders.stream().map(Order::getPharmacyId).distinct().toList())
                .stream().collect(java.util.stream.Collectors.toMap(Pharmacy::getId, p -> p));
    }

    private Map<Long, Long> itemCounts(List<Order> orders) {
        if (orders.isEmpty()) return Map.of();
        Map<Long, Long> counts = new HashMap<>();
        orderItemRepository.findByOrderIdIn(orders.stream().map(Order::getId).toList())
                .forEach(i -> counts.merge(i.getOrderId(), (long) i.getQuantity(), Long::sum));
        return counts;
    }

    private OrderStatus parseStatus(String value) {
        try {
            return OrderStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("Unknown order status: " + value);
        }
    }

    private double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        try {
            return mapsService.route(lat1, lng1, lat2, lng2).distanceKm();
        } catch (RuntimeException ex) {
            log.debug("Route lookup failed, using straight-line distance: {}", ex.getMessage());
            return Haversine.distanceKm(lat1, lng1, lat2, lng2);
        }
    }

    private String generateOrderNumber() {
        String date = DateTimeFormatter.ofPattern("yyyyMMdd")
                .withZone(ZoneId.of("Asia/Kolkata")).format(Instant.now());
        return "MD" + date + Crypto.numericCode(5);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return phone;
        return "XXXXXX" + phone.substring(phone.length() - 4);
    }
}
