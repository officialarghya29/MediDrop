package com.medidrop.pharmacy;

import com.medidrop.admin.ReportingService;
import com.medidrop.common.ApiResponse;
import com.medidrop.common.FileValidation;
import com.medidrop.common.exception.ApiException;
import com.medidrop.security.CurrentUser;
import com.medidrop.storage.FileStorage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pharmacy")
@RequiredArgsConstructor
public class PharmacyProfileController {

    private final PharmacyService pharmacyService;
    private final PharmacyRepository pharmacyRepository;
    private final PharmacyDocumentRepository documentRepository;
    private final FileStorage fileStorage;
    private final ReportingService reportingService;

    @GetMapping("/profile")
    public ApiResponse<PharmacyDtos.PharmacyDetail> profile() {
        Pharmacy pharmacy = pharmacyService.requirePharmacy(CurrentUser.id());
        return ApiResponse.ok(pharmacyService.detail(pharmacy.getId()));
    }

    @GetMapping("/dashboard")
    public ApiResponse<PharmacyDtos.DashboardResponse> dashboard() {
        return ApiResponse.ok(pharmacyService.dashboard(CurrentUser.id()));
    }

    @PutMapping("/hours")
    public ApiResponse<PharmacyDtos.PharmacyDetail> hours(
            @Valid @RequestBody PharmacyDtos.OpeningHoursRequest request) {
        return ApiResponse.ok("Operating hours updated", pharmacyService.updateHours(CurrentUser.id(), request));
    }

    @PostMapping("/accepting-orders")
    public ApiResponse<PharmacyDtos.DashboardResponse> accepting(
            @Valid @RequestBody PharmacyDtos.StatusUpdateRequest request) {
        return ApiResponse.ok("Updated",
                pharmacyService.setAcceptingOrders(CurrentUser.id(), Boolean.TRUE.equals(request.acceptingOrders())));
    }

    @GetMapping("/documents")
    public ApiResponse<List<PharmacyDtos.DocumentResponse>> documents() {
        Pharmacy pharmacy = pharmacyService.requirePharmacy(CurrentUser.id());
        return ApiResponse.ok(documentRepository.findByPharmacyId(pharmacy.getId()).stream()
                .map(d -> new PharmacyDtos.DocumentResponse(d.getId(), d.getDocumentType(), d.getFileName(),
                        d.getStatus(), d.getCreatedAt(), d.getRemarks()))
                .toList());
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PharmacyDtos.DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "DRUG_LICENSE") String documentType) {
        Pharmacy pharmacy = pharmacyService.requirePharmacy(CurrentUser.id());
        if (file.isEmpty()) throw ApiException.badRequest("Select a document to upload");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!List.of("application/pdf", "image/jpeg", "image/png").contains(contentType)) {
            throw ApiException.badRequest("Only PDF, JPEG or PNG documents are accepted");
        }
        if (file.getSize() > 8 * 1024 * 1024) {
            throw ApiException.badRequest("Document must be smaller than 8 MB");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the uploaded document");
        }
        FileValidation.assertMagicBytes(content, contentType);
        FileStorage.StoredFile stored;
        try (InputStream in = new ByteArrayInputStream(content)) {
            stored = fileStorage.store(in, file.getOriginalFilename(), contentType, "pharmacy-documents");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to store document", e);
        }
        PharmacyDocument document = new PharmacyDocument();
        document.setPharmacyId(pharmacy.getId());
        document.setDocumentType(documentType.toUpperCase().replaceAll("[^A-Z_]", ""));
        document.setFileName(safeName(file.getOriginalFilename()));
        document.setStorageKey(stored.key());
        document.setContentType(contentType);
        document.setStatus(com.medidrop.common.ApprovalStatus.PENDING);
        documentRepository.save(document);
        return ApiResponse.ok("Document uploaded for verification",
                new PharmacyDtos.DocumentResponse(document.getId(), document.getDocumentType(),
                        document.getFileName(), document.getStatus(), document.getCreatedAt(), null));
    }

    private String safeName(String name) {
        if (name == null || name.isBlank()) return "document";
        String cleaned = name.replaceAll("[\\\\/\r\n]", "_").trim();
        return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
    }

    @GetMapping("/analytics")
    public ApiResponse<ReportingService.PharmacyAnalytics> analytics(@RequestParam(defaultValue = "30") int days) {
        Pharmacy pharmacy = pharmacyService.requirePharmacy(CurrentUser.id());
        return ApiResponse.ok(reportingService.pharmacyAnalytics(pharmacy.getId(), days));
    }
}
