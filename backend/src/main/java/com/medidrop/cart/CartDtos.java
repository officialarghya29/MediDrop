package com.medidrop.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class CartDtos {

    private CartDtos() {
    }

    public record AddItemRequest(@NotNull Long inventoryId, @NotNull @Min(1) @Max(10) Integer quantity) {
    }

    public record UpdateItemRequest(@NotNull @Min(1) @Max(10) Integer quantity) {
    }

    public record CartLine(Long id, Long inventoryId, Long medicineId, String medicineName, String brand,
                           String strength, String packSize, boolean prescriptionRequired,
                           BigDecimal unitPrice, int quantity, int availableQuantity, BigDecimal lineTotal,
                           boolean stockSufficient) {
    }

    public record CartView(Long cartId, Long pharmacyId, String pharmacyName, double distanceKm,
                           List<CartLine> lines, int itemCount, BigDecimal subtotal, BigDecimal deliveryFee,
                           BigDecimal taxAmount, BigDecimal totalAmount, List<String> warnings,
                           boolean checkoutReady, String prescriptionNotice) {
    }
}
