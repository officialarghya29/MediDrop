package com.medidrop.medicine;

import com.medidrop.common.ApiResponse;
import com.medidrop.common.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medicines")
@RequiredArgsConstructor
@Validated
public class MedicineController {

    private final MedicineService medicineService;

    @GetMapping
    public ApiResponse<PageResponse<MedicineDtos.MedicineSummary>> browse(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(medicineService.browse(page, size));
    }

    @GetMapping("/search")
    public ApiResponse<MedicineDtos.SearchResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long manufacturerId,
            @RequestParam(required = false) Boolean prescriptionRequired,
            @RequestParam(defaultValue = "relevance") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ApiResponse.ok(medicineService.search(q, categoryId, manufacturerId, prescriptionRequired,
                sort, page, size));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<String>> suggestions(@RequestParam String q,
                                                 @RequestParam(defaultValue = "6") int limit) {
        return ApiResponse.ok(medicineService.suggestions(q, limit));
    }

    @GetMapping("/categories")
    public ApiResponse<List<MedicineDtos.CategoryResponse>> categories() {
        return ApiResponse.ok(medicineService.categories());
    }

    @GetMapping("/manufacturers")
    public ApiResponse<List<MedicineDtos.ManufacturerResponse>> manufacturers() {
        return ApiResponse.ok(medicineService.manufacturers());
    }

    @GetMapping("/{id}")
    public ApiResponse<MedicineDtos.MedicineDetail> detail(@PathVariable Long id,
                                                           @RequestParam(required = false) Double lat,
                                                           @RequestParam(required = false) Double lng) {
        return ApiResponse.ok(medicineService.detail(id, lat, lng));
    }
}
