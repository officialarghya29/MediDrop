package com.medidrop.prescription;

import com.medidrop.common.ApiResponse;
import com.medidrop.common.PageResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pharmacy/prescriptions")
@RequiredArgsConstructor
public class PharmacyPrescriptionController {

    private final PrescriptionService prescriptionService;

    @GetMapping
    public ApiResponse<PageResponse<PrescriptionDtos.PendingReview>> queue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(prescriptionService.pharmacyQueue(CurrentUser.id(), status, page, size));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id) {
        Resource resource = prescriptionService.loadFile(CurrentUser.id(), CurrentUser.role(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(prescriptionService.fileContentType(id)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"prescription-" + id + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .body(resource);
    }

    @GetMapping("/{id}/reviews")
    public ApiResponse<List<PrescriptionDtos.ReviewHistory>> reviews(@PathVariable Long id) {
        return ApiResponse.ok(prescriptionService.reviews(id));
    }

    @PostMapping("/{id}/review")
    public ApiResponse<PrescriptionDtos.PrescriptionResponse> review(
            @PathVariable Long id,
            @Valid @RequestBody PrescriptionDtos.ReviewDecisionRequest request) {
        return ApiResponse.ok("Review recorded", prescriptionService.review(CurrentUser.id(), id, request));
    }
}
