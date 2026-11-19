package com.medidrop.inventory;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pharmacy/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ApiResponse<List<InventoryDtos.InventoryRow>> list() {
        return ApiResponse.ok(inventoryService.list(CurrentUser.id()));
    }

    @PostMapping
    public ApiResponse<InventoryDtos.InventoryRow> upsert(@Valid @RequestBody InventoryDtos.UpsertRequest request) {
        return ApiResponse.ok("Inventory saved", inventoryService.upsert(CurrentUser.id(), request));
    }

    @PostMapping("/medicines")
    public ApiResponse<InventoryDtos.InventoryRow> createMedicine(
            @Valid @RequestBody InventoryDtos.CreateMedicineRequest request) {
        return ApiResponse.ok("Medicine added to catalogue", inventoryService.createMedicine(CurrentUser.id(), request));
    }

    @PatchMapping("/{id}/stock")
    public ApiResponse<InventoryDtos.InventoryRow> adjust(@PathVariable Long id,
                                                          @Valid @RequestBody InventoryDtos.StockAdjustment request) {
        return ApiResponse.ok("Stock updated", inventoryService.adjustStock(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<InventoryDtos.InventoryRow> deactivate(@PathVariable Long id) {
        return ApiResponse.ok("Listing deactivated", inventoryService.deactivate(CurrentUser.id(), id));
    }

    @GetMapping("/{id}/batches")
    public ApiResponse<List<InventoryDtos.BatchResponse>> batches(@PathVariable Long id) {
        return ApiResponse.ok(inventoryService.batches(CurrentUser.id(), id));
    }

    @PostMapping("/{id}/batches")
    public ApiResponse<InventoryDtos.BatchResponse> addBatch(@PathVariable Long id,
                                                             @Valid @RequestBody InventoryDtos.BatchRequest request) {
        return ApiResponse.ok("Batch added", inventoryService.addBatch(CurrentUser.id(), id, request));
    }

    @GetMapping("/{id}/ledger")
    public ApiResponse<List<InventoryDtos.TransactionResponse>> ledger(@PathVariable Long id) {
        return ApiResponse.ok(inventoryService.ledger(CurrentUser.id(), id));
    }

    @GetMapping("/low-stock")
    public ApiResponse<List<InventoryDtos.LowStockAlert>> lowStock() {
        return ApiResponse.ok(inventoryService.lowStock(CurrentUser.id()));
    }

    @GetMapping("/expiring")
    public ApiResponse<List<InventoryDtos.BatchResponse>> expiring(@RequestParam(defaultValue = "90") int days) {
        return ApiResponse.ok(inventoryService.expiring(CurrentUser.id(), Math.min(Math.max(days, 1), 365)));
    }
}
