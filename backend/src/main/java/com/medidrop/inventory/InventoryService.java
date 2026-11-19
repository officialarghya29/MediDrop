package com.medidrop.inventory;

import com.medidrop.audit.AuditService;
import com.medidrop.common.Haversine;
import com.medidrop.common.exception.ApiException;
import com.medidrop.medicine.Medicine;
import com.medidrop.medicine.MedicineCategoryRepository;
import com.medidrop.medicine.MedicineRepository;
import com.medidrop.pharmacy.Pharmacy;
import com.medidrop.pharmacy.PharmacyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final PharmacyInventoryRepository inventoryRepository;
    private final InventoryBatchRepository batchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final MedicineRepository medicineRepository;
    private final MedicineCategoryRepository categoryRepository;
    private final PharmacyRepository pharmacyRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<InventoryDtos.InventoryRow> list(Long userId) {
        Pharmacy pharmacy = pharmacy(userId);
        List<PharmacyInventory> rows = inventoryRepository.findByPharmacyIdOrderByUpdatedAtDesc(pharmacy.getId());
        Map<Long, Medicine> medicines = medicineRepository.findAllById(
                        rows.stream().map(PharmacyInventory::getMedicineId).toList())
                .stream().collect(Collectors.toMap(Medicine::getId, m -> m));
        return rows.stream().map(row -> {
            Medicine medicine = medicines.get(row.getMedicineId());
            LocalDate expiry = batchRepository.findByInventoryIdOrderByExpiryDateAsc(row.getId()).stream()
                    .filter(b -> b.getQuantity() > 0)
                    .map(InventoryBatch::getExpiryDate)
                    .findFirst().orElse(null);
            return new InventoryDtos.InventoryRow(row.getId(), row.getMedicineId(),
                    medicine == null ? "Unknown medicine" : medicine.getName(),
                    medicine == null ? null : medicine.getBrand(),
                    medicine == null ? null : medicine.getStrength(),
                    medicine == null ? null : medicine.getPackSize(),
                    medicine != null && medicine.isPrescriptionRequired(),
                    row.getPrice(), row.getMrp(), row.getStockQuantity(), row.availableQuantity(),
                    row.getLowStockThreshold(), row.isActive(), expiry, row.getVersion());
        }).toList();
    }

    @Transactional
    public InventoryDtos.InventoryRow upsert(Long userId, InventoryDtos.UpsertRequest request) {
        Pharmacy pharmacy = approvedPharmacy(userId);
        Medicine medicine = medicineRepository.findById(request.medicineId())
                .orElseThrow(() -> ApiException.notFound("Medicine not found in catalogue"));
        if (request.mrp().compareTo(request.price()) < 0) {
            throw ApiException.badRequest("Selling price cannot exceed MRP");
        }
        PharmacyInventory inventory = inventoryRepository
                .findByPharmacyIdAndMedicineId(pharmacy.getId(), medicine.getId())
                .orElseGet(() -> {
                    PharmacyInventory fresh = new PharmacyInventory();
                    fresh.setPharmacyId(pharmacy.getId());
                    fresh.setMedicineId(medicine.getId());
                    return fresh;
                });
        int previousStock = inventory.getStockQuantity();
        inventory.setPrice(request.price());
        inventory.setMrp(request.mrp());
        inventory.setStockQuantity(request.stockQuantity());
        if (request.lowStockThreshold() != null) inventory.setLowStockThreshold(request.lowStockThreshold());
        if (request.active() != null) inventory.setActive(request.active());
        inventory = inventoryRepository.save(inventory);

        if (previousStock != request.stockQuantity()) {
            record(inventory, InventoryTransaction.Type.ADJUSTMENT,
                    request.stockQuantity() - previousStock, "Manual stock update", userId, null);
        }
        Long savedId = inventory.getId();
        return list(userId).stream().filter(r -> r.id().equals(savedId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Inventory row missing"));
    }

    @Transactional
    public InventoryDtos.InventoryRow adjustStock(Long userId, Long inventoryId,
                                                  InventoryDtos.StockAdjustment request) {
        Pharmacy pharmacy = approvedPharmacy(userId);
        PharmacyInventory inventory = inventoryRepository.findByIdAndPharmacyId(inventoryId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        int updated = inventory.getStockQuantity() + request.delta();
        if (updated < 0) {
            throw ApiException.badRequest("Stock cannot become negative");
        }
        inventory.setStockQuantity(updated);
        inventory = inventoryRepository.save(inventory);
        record(inventory, InventoryTransaction.Type.ADJUSTMENT, request.delta(), request.reason(), userId, null);
        return list(userId).stream().filter(r -> r.id().equals(inventoryId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Inventory row missing"));
    }

    @Transactional
    public InventoryDtos.InventoryRow deactivate(Long userId, Long inventoryId) {
        Pharmacy pharmacy = approvedPharmacy(userId);
        PharmacyInventory inventory = inventoryRepository.findByIdAndPharmacyId(inventoryId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        inventory.setActive(false);
        inventory = inventoryRepository.save(inventory);
        auditService.recordForUser(userId, "PHARMACY", "INVENTORY_DEACTIVATED", "PharmacyInventory",
                inventoryId, null);
        return list(userId).stream().filter(r -> r.id().equals(inventoryId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Inventory row missing"));
    }

    @Transactional
    public InventoryDtos.BatchResponse addBatch(Long userId, Long inventoryId,
                                                InventoryDtos.BatchRequest request) {
        Pharmacy pharmacy = approvedPharmacy(userId);
        PharmacyInventory inventory = inventoryRepository.findByIdAndPharmacyId(inventoryId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        if (request.expiryDate().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("Cannot add stock with an expiry date in the past");
        }
        InventoryBatch batch = new InventoryBatch();
        batch.setInventoryId(inventory.getId());
        batch.setBatchNumber(request.batchNumber().trim());
        batch.setExpiryDate(request.expiryDate());
        batch.setQuantity(request.quantity());
        batch.setCostPrice(request.costPrice());
        batchRepository.save(batch);

        inventory.setStockQuantity(inventory.getStockQuantity() + request.quantity());
        inventory = inventoryRepository.save(inventory);
        record(inventory, InventoryTransaction.Type.RESTOCK, request.quantity(),
                "Batch " + batch.getBatchNumber(), userId, null);
        return new InventoryDtos.BatchResponse(batch.getId(), batch.getBatchNumber(), batch.getExpiryDate(),
                batch.getQuantity(), batch.getCostPrice(), batch.getExpiryDate().isBefore(LocalDate.now().plusDays(90)));
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.BatchResponse> batches(Long userId, Long inventoryId) {
        Pharmacy pharmacy = pharmacy(userId);
        inventoryRepository.findByIdAndPharmacyId(inventoryId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        return batchRepository.findByInventoryIdOrderByExpiryDateAsc(inventoryId).stream()
                .map(b -> new InventoryDtos.BatchResponse(b.getId(), b.getBatchNumber(), b.getExpiryDate(),
                        b.getQuantity(), b.getCostPrice(),
                        b.getExpiryDate().isBefore(LocalDate.now().plusDays(90))))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.TransactionResponse> ledger(Long userId, Long inventoryId) {
        Pharmacy pharmacy = pharmacy(userId);
        inventoryRepository.findByIdAndPharmacyId(inventoryId, pharmacy.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        return transactionRepository.findTop50ByInventoryIdOrderByCreatedAtDesc(inventoryId).stream()
                .map(t -> new InventoryDtos.TransactionResponse(t.getId(), t.getType().name(),
                        t.getQuantityDelta(), t.getBalanceAfter(), t.getReason(), t.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.LowStockAlert> lowStock(Long userId) {
        Pharmacy pharmacy = pharmacy(userId);
        List<PharmacyInventory> rows = inventoryRepository.findLowStock(pharmacy.getId());
        Map<Long, Medicine> medicines = medicineRepository.findAllById(
                        rows.stream().map(PharmacyInventory::getMedicineId).toList())
                .stream().collect(Collectors.toMap(Medicine::getId, m -> m));
        return rows.stream()
                .sorted(Comparator.comparingInt(PharmacyInventory::getStockQuantity))
                .map(r -> new InventoryDtos.LowStockAlert(r.getId(), r.getMedicineId(),
                        medicines.containsKey(r.getMedicineId()) ? medicines.get(r.getMedicineId()).getName() : "-",
                        r.getStockQuantity(), r.getLowStockThreshold()))
                .toList();
    }

    @Transactional
    public InventoryDtos.InventoryRow createMedicine(Long userId, InventoryDtos.CreateMedicineRequest request) {
        Pharmacy pharmacy = approvedPharmacy(userId);
        if (!categoryRepository.existsById(request.categoryId())) {
            throw ApiException.badRequest("Unknown medicine category");
        }
        Medicine medicine = new Medicine();
        medicine.setName(request.name().trim());
        medicine.setBrand(request.brand());
        medicine.setGenericName(request.genericName());
        medicine.setStrength(request.strength());
        medicine.setDosageForm(request.dosageForm());
        medicine.setPackSize(request.packSize());
        medicine.setCategoryId(request.categoryId());
        medicine.setPrescriptionRequired(Boolean.TRUE.equals(request.prescriptionRequired()));
        medicine.setDescription(request.description());
        medicine.setWarnings(request.warnings());
        medicine.setDemo(true);
        medicineRepository.save(medicine);

        BigDecimal price = request.price() == null ? new BigDecimal("10.00") : request.price();
        BigDecimal mrp = request.mrp() == null ? price : request.mrp();
        if (mrp.compareTo(price) < 0) throw ApiException.badRequest("Selling price cannot exceed MRP");
        PharmacyInventory inventory = new PharmacyInventory();
        inventory.setPharmacyId(pharmacy.getId());
        inventory.setMedicineId(medicine.getId());
        inventory.setPrice(price);
        inventory.setMrp(mrp);
        inventory.setStockQuantity(request.stockQuantity() == null ? 0 : request.stockQuantity());
        inventory = inventoryRepository.save(inventory);
        if (inventory.getStockQuantity() > 0) {
            record(inventory, InventoryTransaction.Type.RESTOCK, inventory.getStockQuantity(),
                    "Initial stock", userId, null);
        }
        Long savedId = inventory.getId();
        return list(userId).stream().filter(r -> r.id().equals(savedId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Inventory row missing"));
    }

    @Transactional(readOnly = true)
    public List<InventoryDtos.BatchResponse> expiring(Long userId, int days) {
        Pharmacy pharmacy = pharmacy(userId);
        return batchRepository.findExpiringForPharmacy(pharmacy.getId(), LocalDate.now().plusDays(days)).stream()
                .map(b -> new InventoryDtos.BatchResponse(b.getId(), b.getBatchNumber(), b.getExpiryDate(),
                        b.getQuantity(), b.getCostPrice(), true))
                .toList();
    }

    private void record(PharmacyInventory inventory, InventoryTransaction.Type type, int delta,
                        String reason, Long actorId, Long orderId) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setInventoryId(inventory.getId());
        tx.setType(type);
        tx.setQuantityDelta(delta);
        tx.setBalanceAfter(inventory.getStockQuantity());
        tx.setReason(reason);
        tx.setActorUserId(actorId);
        tx.setOrderId(orderId);
        transactionRepository.save(tx);
    }

    private Pharmacy pharmacy(Long userId) {
        return pharmacyRepository.findByOwnerUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Pharmacy profile not found"));
    }

    private Pharmacy approvedPharmacy(Long userId) {
        Pharmacy pharmacy = pharmacy(userId);
        if (pharmacy.getStatus() != com.medidrop.common.ApprovalStatus.APPROVED) {
            throw ApiException.forbidden("Pharmacy must be verified before managing inventory");
        }
        return pharmacy;
    }

    @SuppressWarnings("unused")
    private double distance(double lat1, double lng1, double lat2, double lng2) {
        return Haversine.distanceKm(lat1, lng1, lat2, lng2);
    }
}
