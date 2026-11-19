package com.medidrop.user;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final ProfileService profileService;

    @GetMapping("/me")
    public ApiResponse<UserDtos.ProfileResponse> me() {
        return ApiResponse.ok(profileService.profile(CurrentUser.id()));
    }

    @PutMapping("/me")
    public ApiResponse<UserDtos.ProfileResponse> update(@Valid @RequestBody UserDtos.UpdateProfileRequest request) {
        return ApiResponse.ok("Profile updated", profileService.updateProfile(CurrentUser.id(), request));
    }

    @PostMapping("/me/deletion-request")
    public ApiResponse<Void> requestDeletion(@RequestBody(required = false) UserDtos.DeletionRequest request) {
        profileService.requestDeletion(CurrentUser.id(), request == null ? null : request.reason());
        return ApiResponse.ok("Account deletion requested", null);
    }

    @GetMapping("/addresses")
    public ApiResponse<List<UserDtos.AddressResponse>> addresses() {
        return ApiResponse.ok(profileService.addresses(CurrentUser.id()));
    }

    @PostMapping("/addresses")
    public ApiResponse<UserDtos.AddressResponse> addAddress(@Valid @RequestBody UserDtos.AddressRequest request) {
        return ApiResponse.ok("Address saved", profileService.addAddress(CurrentUser.id(), request));
    }

    @PutMapping("/addresses/{id}")
    public ApiResponse<UserDtos.AddressResponse> updateAddress(@PathVariable Long id,
                                                               @Valid @RequestBody UserDtos.AddressRequest request) {
        return ApiResponse.ok("Address updated", profileService.updateAddress(CurrentUser.id(), id, request));
    }

    @DeleteMapping("/addresses/{id}")
    public ApiResponse<Void> deleteAddress(@PathVariable Long id) {
        profileService.deleteAddress(CurrentUser.id(), id);
        return ApiResponse.ok("Address removed", null);
    }
}
