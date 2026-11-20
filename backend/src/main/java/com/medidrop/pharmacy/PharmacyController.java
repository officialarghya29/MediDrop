package com.medidrop.pharmacy;

import com.medidrop.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/pharmacies")
@RequiredArgsConstructor
@Validated
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody PharmacyDtos.RegistrationRequest request) {
        pharmacyService.register(request);
        return ApiResponse.ok("Registration submitted. Your licence will be verified before activation.", null);
    }

    @GetMapping("/nearby")
    public ApiResponse<List<PharmacyDtos.NearbyPharmacy>> nearby(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double lat,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double lng,
            @RequestParam(defaultValue = "10") @DecimalMin("1.0") @DecimalMax("50.0") double radiusKm) {
        return ApiResponse.ok(pharmacyService.nearby(lat, lng, radiusKm, false));
    }

    @GetMapping("/{id}")
    public ApiResponse<PharmacyDtos.PharmacyDetail> detail(@PathVariable Long id) {
        return ApiResponse.ok(pharmacyService.detail(id));
    }
}
