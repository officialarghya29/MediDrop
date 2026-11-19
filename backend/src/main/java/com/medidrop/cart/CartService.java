package com.medidrop.cart;

import com.medidrop.common.ApprovalStatus;
import com.medidrop.common.Haversine;
import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import com.medidrop.inventory.PharmacyInventory;
import com.medidrop.inventory.PharmacyInventoryRepository;
import com.medidrop.medicine.Medicine;
import com.medidrop.medicine.MedicineRepository;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import com.medidrop.pricing.PricingService;
import com.medidrop.user.Address;
import com.medidrop.user.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final PharmacyInventoryRepository inventoryRepository;
    private final MedicineRepository medicineRepository;
    private final PharmacyRepository pharmacyRepository;
    private final AddressRepository addressRepository;
    private final PricingService pricingService;
    private final MedidropProperties properties;

    public record SnapshotLine(Long inventoryId, Long medicineId, String medicineName, int quantity,
                               BigDecimal unitPrice, boolean prescriptionRequired, PharmacyInventory inventory) {
    }

    public record CheckoutSnapshot(Long pharmacyId, List<SnapshotLine> lines) {
        public BigDecimal subtotal() {
            return lines.stream().map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    @Transactional
    public CartDtos.CartView view(Long customerId) {
        Cart cart = getOrCreate(customerId);
        return buildView(cart);
    }

    @Transactional
    public CartDtos.CartView addItem(Long customerId, Long inventoryId, int quantity) {
        if (quantity < 1) throw ApiException.badRequest("Quantity must be at least 1");
        int max = properties.getCommerce().getCartMaxQuantityPerLine();
        Cart cart = getOrCreate(customerId);
        PharmacyInventory inventory = inventoryRepository.findById(inventoryId)
                .filter(PharmacyInventory::isActive)
                .orElseThrow(() -> ApiException.notFound("This medicine is not available"));
        Pharmacy pharmacy = pharmacyRepository.findById(inventory.getPharmacyId())
                .orElseThrow(() -> ApiException.notFound("Pharmacy not found"));
        if (pharmacy.getStatus() != ApprovalStatus.APPROVED || !pharmacy.isActive()) {
            throw ApiException.badRequest("This pharmacy is currently unavailable");
        }
        if (!pharmacy.isAcceptingOrders()) {
            throw ApiException.badRequest("This pharmacy is not accepting new orders right now");
        }
        Medicine medicine = medicineRepository.findByIdAndActiveTrue(inventory.getMedicineId())
                .orElseThrow(() -> ApiException.notFound("Medicine is no longer available"));

        List<CartItem> existing = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        CartItem item = existing.stream()
                .filter(i -> i.getInventoryId().equals(inventoryId))
                .findFirst()
                .orElse(null);
        int newQuantity = (item == null ? 0 : item.getQuantity()) + quantity;
        if (newQuantity > max) {
            throw ApiException.badRequest("Maximum " + max + " units per medicine");
        }
        if (newQuantity > inventory.availableQuantity()) {
            throw ApiException.conflict("Only " + inventory.availableQuantity()
                    + " unit(s) available at this pharmacy");
        }
        boolean otherPharmacy = existing.stream()
                .anyMatch(i -> !i.getPharmacyId().equals(inventory.getPharmacyId()));
        if (otherPharmacy) {
            throw ApiException.conflict("Your cart contains items from another pharmacy. " +
                    "Complete or clear that cart before adding items from a different pharmacy.");
        }

        if (item == null) {
            item = new CartItem();
            item.setCartId(cart.getId());
            item.setInventoryId(inventoryId);
            item.setMedicineId(medicine.getId());
            item.setPharmacyId(inventory.getPharmacyId());
            item.setQuantity(newQuantity);
        } else {
            item.setQuantity(newQuantity);
        }
        item.setUnitPriceSnapshot(inventory.getPrice());
        cartItemRepository.save(item);
        return buildView(cart);
    }

    @Transactional
    public CartDtos.CartView updateItem(Long customerId, Long itemId, int quantity) {
        Cart cart = getOrCreate(customerId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));
        if (quantity < 1) {
            cartItemRepository.delete(item);
            return buildView(cart);
        }
        if (quantity > properties.getCommerce().getCartMaxQuantityPerLine()) {
            throw ApiException.badRequest("Maximum " + properties.getCommerce().getCartMaxQuantityPerLine()
                    + " units per medicine");
        }
        PharmacyInventory inventory = inventoryRepository.findById(item.getInventoryId())
                .orElseThrow(() -> ApiException.notFound("Listing no longer exists"));
        if (quantity > inventory.availableQuantity()) {
            throw ApiException.conflict("Only " + inventory.availableQuantity() + " unit(s) available");
        }
        item.setQuantity(quantity);
        cartItemRepository.save(item);
        return buildView(cart);
    }

    @Transactional
    public CartDtos.CartView removeItem(Long customerId, Long itemId) {
        Cart cart = getOrCreate(customerId);
        CartItem item = cartItemRepository.findByIdAndCartId(itemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Cart item not found"));
        cartItemRepository.delete(item);
        return buildView(cart);
    }

    @Transactional
    public CartDtos.CartView clear(Long customerId) {
        Cart cart = getOrCreate(customerId);
        cartItemRepository.deleteByCartId(cart.getId());
        return buildView(cart);
    }

    @Transactional(readOnly = true)
    public CheckoutSnapshot checkoutSnapshot(Long customerId) {
        Cart cart = cartRepository.findByCustomerId(customerId)
                .orElseThrow(() -> ApiException.badRequest("Your cart is empty"));
        List<CartItem> items = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (items.isEmpty()) throw ApiException.badRequest("Your cart is empty");

        List<SnapshotLine> lines = new ArrayList<>();
        for (CartItem item : items) {
            PharmacyInventory inventory = inventoryRepository.findById(item.getInventoryId())
                    .orElseThrow(() -> ApiException.conflict("A medicine in your cart is no longer listed"));
            Medicine medicine = medicineRepository.findById(inventory.getMedicineId())
                    .orElseThrow(() -> ApiException.conflict("A medicine in your cart is unavailable"));
            if (!inventory.isActive() || inventory.availableQuantity() < item.getQuantity()) {
                throw ApiException.conflict("Stock changed for " + medicine.getName() + ". Available: "
                        + Math.max(0, inventory.availableQuantity()));
            }
            lines.add(new SnapshotLine(inventory.getId(), medicine.getId(), medicine.getName(),
                    item.getQuantity(), inventory.getPrice(), medicine.isPrescriptionRequired(), inventory));
        }
        Long pharmacyId = items.get(0).getPharmacyId();
        if (items.stream().anyMatch(i -> !i.getPharmacyId().equals(pharmacyId))) {
            throw ApiException.conflict("Cart items must belong to a single pharmacy");
        }
        return new CheckoutSnapshot(pharmacyId, lines);
    }

    @Transactional
    public void clearFor(Long customerId) {
        cartRepository.findByCustomerId(customerId)
                .ifPresent(cart -> cartItemRepository.deleteByCartId(cart.getId()));
    }

    @Transactional
    public Cart getOrCreate(Long customerId) {
        return cartRepository.findByCustomerId(customerId).orElseGet(() -> {
            Cart cart = new Cart();
            cart.setCustomerId(customerId);
            return cartRepository.save(cart);
        });
    }

    private CartDtos.CartView buildView(Cart cart) {
        List<CartItem> items = cartItemRepository.findByCartIdOrderByCreatedAtAsc(cart.getId());
        if (items.isEmpty()) {
            return new CartDtos.CartView(cart.getId(), null, null, 0, List.of(), 0,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    List.of(), false, null);
        }
        List<Long> medicineIds = items.stream().map(CartItem::getMedicineId).toList();
        Map<Long, Medicine> medicines = medicineRepository.findAllById(medicineIds).stream()
                .collect(Collectors.toMap(Medicine::getId, m -> m));
        Pharmacy pharmacy = pharmacyRepository.findById(items.get(0).getPharmacyId()).orElse(null);

        double distance = 0;
        if (pharmacy != null) {
            Address address = addressRepository
                    .findByUserIdOrderByDefaultAddressDescCreatedAtDesc(cart.getCustomerId())
                    .stream().findFirst().orElse(null);
            if (address != null) {
                distance = Haversine.distanceKm(address.getLatitude(), address.getLongitude(),
                        pharmacy.getLatitude(), pharmacy.getLongitude());
            }
        }

        List<CartDtos.CartLine> lines = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        boolean stockOk = true;
        boolean rx = false;

        for (CartItem item : items) {
            Medicine medicine = medicines.get(item.getMedicineId());
            PharmacyInventory inventory = inventoryRepository.findById(item.getInventoryId()).orElse(null);
            int available = inventory == null ? 0 : inventory.availableQuantity();
            BigDecimal unitPrice = inventory == null ? item.getUnitPriceSnapshot() : inventory.getPrice();
            if (unitPrice == null) unitPrice = BigDecimal.ZERO;
            boolean sufficient = available >= item.getQuantity();
            if (!sufficient) {
                stockOk = false;
                warnings.add((medicine == null ? "An item" : medicine.getName())
                        + " has only " + available + " unit(s) left");
            }
            if (medicine != null && medicine.isPrescriptionRequired()) rx = true;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            subtotal = subtotal.add(lineTotal);
            lines.add(new CartDtos.CartLine(item.getId(), item.getInventoryId(), item.getMedicineId(),
                    medicine == null ? "Unavailable medicine" : medicine.getName(),
                    medicine == null ? null : medicine.getBrand(),
                    medicine == null ? null : medicine.getStrength(),
                    medicine == null ? null : medicine.getPackSize(),
                    medicine != null && medicine.isPrescriptionRequired(),
                    unitPrice, item.getQuantity(), available, lineTotal, sufficient));
        }

        var totals = pricingService.totals(subtotal, null, distance);
        boolean pharmacyUsable = pharmacy != null && pharmacy.isAcceptingOrders() && stockOk;
        if (pharmacyUsable && pharmacy != null && distance > pharmacy.getDeliveryRadiusKm()) {
            warnings.add("Your saved address is outside this pharmacy's delivery radius");
            pharmacyUsable = false;
        }
        if (pharmacy != null && subtotal.compareTo(pharmacy.getMinOrderValue()) < 0) {
            warnings.add("Minimum order value for this pharmacy is ₹"
                    + pharmacy.getMinOrderValue().stripTrailingZeros().toPlainString());
            pharmacyUsable = false;
        }
        return new CartDtos.CartView(cart.getId(), items.get(0).getPharmacyId(),
                pharmacy == null ? null : pharmacy.getName(), Math.round(distance * 10.0) / 10.0,
                lines, items.stream().mapToInt(CartItem::getQuantity).sum(),
                totals.subtotal(), totals.deliveryFee(), totals.taxAmount(), totals.totalAmount(),
                warnings, pharmacyUsable,
                rx ? "One or more items need a valid prescription reviewed by a licensed pharmacist."
                        : null);
    }
}
