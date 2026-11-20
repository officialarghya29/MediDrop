package com.medidrop.medicine;

import java.math.BigDecimal;
import java.util.List;

public final class MedicineDtos {

    private MedicineDtos() {
    }

    public record CategoryResponse(Long id, String name, String slug, String description,
                                   boolean prescriptionOnly) {
    }

    public record ManufacturerResponse(Long id, String name) {
    }

    public record Availability(Long inventoryId, Long pharmacyId, String pharmacyName, String city,
                               Double distanceKm, BigDecimal price, BigDecimal mrp, int availableQuantity,
                               boolean acceptingOrders) {
    }

    public record MedicineSummary(Long id, String name, String brand, String genericName, String strength,
                                  String dosageForm, String packSize, String manufacturerName,
                                  String categoryName, boolean prescriptionRequired, String imageUrl,
                                  BigDecimal lowestPrice, BigDecimal mrp, int totalStock, long pharmacyCount) {
    }

    public record MedicineDetail(Long id, String name, String brand, String genericName, String strength,
                                 String dosageForm, String packSize, String manufacturerName,
                                 String categoryName, boolean prescriptionRequired, String imageUrl,
                                 String description, String warnings, String scheduleClass,
                                 BigDecimal lowestPrice, BigDecimal mrp, int totalStock,
                                 Availability cheapest, List<Availability> availability) {
    }

    public record SearchResponse(List<MedicineSummary> items, int page, int size, long totalElements,
                                 int totalPages, String suggestion, List<String> suggestions) {
    }

    public record SubstituteOption(Long medicineId, Long inventoryId, String name, String brand,
                                   String genericName, String strength, String dosageForm, String packSize,
                                   boolean prescriptionRequired, String scheduleClass, Long pharmacyId,
                                   String pharmacyName, BigDecimal price, BigDecimal mrp,
                                   BigDecimal savingsAmount, int savingsPercent, int availableQuantity) {
    }

    public record SubstituteResponse(Long medicineId, String medicineName, String genericName,
                                     String strength, String dosageForm, String packSize, Long pharmacyId,
                                     String pharmacyName, BigDecimal referencePrice,
                                     boolean requiresPharmacistReview, String notice,
                                     List<SubstituteOption> options) {
    }

    public record AdminMedicineRequest(String name, String brand, String genericName, String strength,
                                       String dosageForm, String packSize, Long manufacturerId, Long categoryId,
                                       boolean prescriptionRequired, String description, String warnings,
                                       String scheduleClass, String imageUrl, Boolean active) {
    }
}
