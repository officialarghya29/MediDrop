package com.medidrop.auth;

import com.medidrop.security.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "Enter a valid 10-digit Indian mobile number") String phone,
            @NotBlank @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
            String password
    ) {
    }

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank String password
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record OtpRequest(@NotBlank @Email String email, @NotBlank String purpose) {
    }

    public record OtpVerifyRequest(
            @NotBlank @Email String email,
            @NotBlank String purpose,
            @NotBlank @Size(min = 4, max = 8) String code
    ) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain letters and numbers")
            String newPassword
    ) {
    }

    public record AuthUser(Long id, String fullName, String email, String phone, Role role,
                           boolean emailVerified, boolean phoneVerified) {
    }

    public record AuthResponse(String accessToken, String refreshToken, long expiresInSeconds,
                               AuthUser user, String devOtp) {
    }

    public record MessageResponse(String message) {
    }
}
