package com.medidrop.auth;

import com.medidrop.common.ApiResponse;
import com.medidrop.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ApiResponse<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request) {
        return ApiResponse.ok("Account created. Verify your email to unlock ordering.",
                authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest request) {
        return ApiResponse.ok("Signed in", authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthDtos.AuthResponse> refresh(@Valid @RequestBody AuthDtos.RefreshRequest request) {
        return ApiResponse.ok(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ApiResponse<AuthDtos.MessageResponse> logout(@RequestBody(required = false) AuthDtos.RefreshRequest request) {
        authService.logout(CurrentUser.id(), request == null ? null : request.refreshToken());
        return ApiResponse.ok(new AuthDtos.MessageResponse("Signed out"));
    }

    @PostMapping("/otp/request")
    public ApiResponse<AuthDtos.MessageResponse> requestOtp(@Valid @RequestBody AuthDtos.OtpRequest request) {
        authService.requestOtp(request);
        return ApiResponse.ok(new AuthDtos.MessageResponse(
                "If the account exists, a verification code has been sent"));
    }

    @PostMapping("/otp/verify")
    public ApiResponse<AuthDtos.AuthResponse> verifyOtp(@Valid @RequestBody AuthDtos.OtpVerifyRequest request) {
        return ApiResponse.ok("Verification complete", authService.verifyOtp(request));
    }

    @PostMapping("/forgot-password")
    public ApiResponse<AuthDtos.MessageResponse> forgotPassword(
            @Valid @RequestBody AuthDtos.ForgotPasswordRequest request) {
        String token = authService.forgotPassword(request.email());
        return ApiResponse.ok(new AuthDtos.MessageResponse(
                token != null ? "Reset token (development): " + token
                        : "If the account exists, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ApiResponse<AuthDtos.MessageResponse> resetPassword(
            @Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request.token(), request.newPassword());
        return ApiResponse.ok(new AuthDtos.MessageResponse("Password updated. Please sign in."));
    }
}
