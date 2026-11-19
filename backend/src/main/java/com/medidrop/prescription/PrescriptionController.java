package com.medidrop.prescription;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer/prescriptions")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    @GetMapping
    public ApiResponse<List<PrescriptionDtos.PrescriptionResponse>> list() {
        return ApiResponse.ok(prescriptionService.customerList(CurrentUser.id()));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PrescriptionDtos.PrescriptionResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) Long pharmacyId) {
        return ApiResponse.ok("Prescription uploaded. A pharmacist will review it shortly.",
                prescriptionService.upload(CurrentUser.id(), file, notes, pharmacyId));
    }

    @GetMapping("/{id}")
    public ApiResponse<PrescriptionDtos.CustomerPrescriptionDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(prescriptionService.customerDetail(CurrentUser.id(), id));
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

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        prescriptionService.delete(CurrentUser.id(), id);
        return ApiResponse.ok("Prescription deleted", null);
    }
}
