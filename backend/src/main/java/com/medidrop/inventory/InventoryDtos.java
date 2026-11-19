package com.medidrop.inventory;

import com.medidrop.common.PageResponse;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class InventoryDtos {

    private InventoryDtos() {
    }

    public record InventoryRow(Long id, Long medicineId, String medicineName, String brand, String strength,
                               String packSize, boolean prescriptionRequired, BigDecimal price, BigDecimal mrp,
                               int stockQuantity, int availableQuantity, int lowStockThreshold, boolean active,
                               LocalDate nearestExpiry, long version) {
    }

    public record UpsertRequest(
            @NotNull Long medicineId,
            @NotNull @DecimalMin("0.01") BigDecimal price,
            @NotNull @DecimalMin("0.01") BigDecimal mrp,
            @NotNull @Min(0) Integer stockQuantity,
            @Min(0) Integer lowStockThreshold,
            Boolean active
    ) {
    }

    public record StockAdjustment(@NotNull Integer delta, @NotBlank @Size(max = 300) String reason) {
    }

    public record BatchRequest(@NotBlank String batchNumber, @NotNull LocalDate expiryDate,
                               @NotNull @Min(0) Integer quantity,
                               @DecimalMin("0.00") BigDecimal costPrice) {
    }

    public record BatchResponse(Long id, String batchNumber, LocalDate expiryDate, int quantity,
                                BigDecimal costPrice, boolean expiringSoon) {
    }

    public record TransactionResponse(Long id, String type, int quantityDelta, int balanceAfter,
                                      String reason, java.time.Instant at) {
    }

    public record CreateMedicineRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 160) String brand,
            @Size(max = 200) String genericName,
            @Size(max = 60) String strength,
            @NotBlank @Size(max = 40) String dosageForm,
            @Size(max = 80) String packSize,
            @NotNull Long categoryId,
            @NotNull Boolean prescriptionRequired,
            @Size(max = 2000) String description,
            @Size(max = 1000) String warnings,
            @DecimalMin("0.01") BigDecimal price,
            @DecimalMin("0.01") BigDecimal mrp,
            @Min(0) Integer stockQuantity
    ) {
    }

    public record LowStockAlert(Long inventoryId, Long medicineId, String medicineName, int stockQuantity,
                                int threshold) {
    }

    public record PagedInventory(PageResponse<InventoryRow> page, List<LowStockAlert> lowStock) {
    }
}
