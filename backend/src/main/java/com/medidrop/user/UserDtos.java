package com.medidrop.user;

import com.medidrop.security.Role;
import jakarta.validation.constraints.*;

import java.time.Instant;

public final class UserDtos {

    private UserDtos() {
    }

    public record ProfileResponse(Long id, String fullName, String email, String phone, Role role,
                                  boolean emailVerified, boolean phoneVerified, String avatarUrl,
                                  Instant createdAt, Instant deletionRequestedAt) {
        public static ProfileResponse of(User user) {
            return new ProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                    user.getRole(), user.isEmailVerified(), user.isPhoneVerified(), user.getAvatarUrl(),
                    user.getCreatedAt(), user.getDeletionRequestedAt());
        }
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,
            @Size(max = 500) String avatarUrl
    ) {
    }

    public record AddressRequest(
            @Size(max = 40) String label,
            @NotBlank @Size(max = 120) String recipientName,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotBlank @Size(max = 200) String line1,
            @Size(max = 200) String line2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Pattern(regexp = "^\\d{6}$", message = "Enter a valid 6-digit PIN code") String pincode,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
            boolean defaultAddress
    ) {
    }

    public record AddressResponse(Long id, String label, String recipientName, String phone, String line1,
                                  String line2, String city, String state, String pincode,
                                  Double latitude, Double longitude, boolean defaultAddress) {
        public static AddressResponse of(Address a) {
            return new AddressResponse(a.getId(), a.getLabel(), a.getRecipientName(), a.getPhone(),
                    a.getLine1(), a.getLine2(), a.getCity(), a.getState(), a.getPincode(),
                    a.getLatitude(), a.getLongitude(), a.isDefaultAddress());
        }
    }

    public record DeletionRequest(String reason) {
    }
}
