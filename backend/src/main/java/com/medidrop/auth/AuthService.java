package com.medidrop.auth;

import com.medidrop.audit.AuditService;
import com.medidrop.common.Crypto;
import com.medidrop.common.exception.ApiException;
import com.medidrop.config.MedidropProperties;
import com.medidrop.notification.EmailSender;
import com.medidrop.notification.NotificationService;
import com.medidrop.notification.SmsSender;
import com.medidrop.security.JwtService;
import com.medidrop.security.Role;
import com.medidrop.user.User;
import com.medidrop.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    public static final String PURPOSE_EMAIL = "EMAIL_VERIFICATION";
    public static final String PURPOSE_PHONE = "PHONE_VERIFICATION";
    public static final String PURPOSE_LOGIN = "LOGIN";

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final MedidropProperties properties;
    private final EmailSender emailSender;
    private final SmsSender smsSender;
    private final NotificationService notificationService;
    private final AuditService auditService;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        if (userRepository.findByPhone(request.phone()).isPresent()) {
            throw ApiException.conflict("An account with this mobile number already exists");
        }
        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(request.phone());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(Role.CUSTOMER);
        userRepository.save(user);

        String otp = createOtp(user, PURPOSE_EMAIL, true);
        notificationService.welcome(user);
        auditService.recordForUser(user.getId(), Role.CUSTOMER.name(), "REGISTER", "User", user.getId(), null);

        return issueTokens(user, properties.getOtp().isExposeInResponse() ? otp : null);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request) {
        String identifier = request.identifier().trim();
        User user = identifier.contains("@")
                ? userRepository.findByEmailIgnoreCase(identifier).orElse(null)
                : userRepository.findByPhone(identifier).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.recordForUser(user == null ? null : user.getId(), null,
                    "LOGIN_FAILED", "User", user == null ? null : user.getId(), "Invalid credentials");
            throw ApiException.unauthorized("Invalid email/mobile or password");
        }
        if (user.isLocked() || !user.isEnabled()) {
            throw ApiException.forbidden("This account is not active. Contact support.");
        }
        if (user.getDeletionRequestedAt() != null) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DELETION_PENDING",
                    "This account is scheduled for deletion. Contact support to restore it.");
        }
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        auditService.recordForUser(user.getId(), user.getRole().name(), "LOGIN_SUCCESS", "User", user.getId(), null);
        return issueTokens(user, null);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(String rawToken) {
        String hash = Crypto.sha256Hex(rawToken);
        RefreshToken token = refreshTokenRepository.findForUpdateByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));
        if (token.isRevoked() || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("Refresh token expired. Please sign in again.");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.unauthorized("Account no longer exists"));
        if (!user.isEnabled() || user.isLocked()) {
            throw ApiException.forbidden("This account is not active");
        }
        token.setRevoked(true);
        refreshTokenRepository.save(token);
        AuthDtos.AuthResponse response = issueTokens(user, null);
        token.setReplacedByHash(Crypto.sha256Hex(response.refreshToken()));
        refreshTokenRepository.save(token);
        return response;
    }

    @Transactional
    public void logout(Long userId, String rawToken) {
        if (rawToken != null && !rawToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(Crypto.sha256Hex(rawToken))
                    .filter(t -> t.getUserId().equals(userId))
                    .ifPresent(t -> {
                        t.setRevoked(true);
                        refreshTokenRepository.save(t);
                    });
        }
        refreshTokenRepository.revokeAllForUser(userId);
    }

    @Transactional
    public void requestOtp(AuthDtos.OtpRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.notFound("No account found for this email"));
        String purpose = normalisePurpose(request.purpose());
        String otp = createOtp(user, purpose, true);
        if (PURPOSE_PHONE.equals(purpose) && user.getPhone() != null) {
            smsSender.send(user.getPhone(), "Your MediDrop verification code is " + otp
                    + ". It expires in " + properties.getOtp().getExpiryMinutes() + " minutes.");
        }
    }

    @Transactional
    public AuthDtos.AuthResponse verifyOtp(AuthDtos.OtpVerifyRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.notFound("No account found for this email"));
        String purpose = normalisePurpose(request.purpose());
        OtpToken token = otpTokenRepository
                .findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(user.getId(), purpose)
                .orElseThrow(() -> ApiException.badRequest("No active verification code. Request a new one."));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Verification code expired. Request a new one.");
        }
        if (token.getAttempts() >= properties.getOtp().getMaxAttempts()) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_ATTEMPTS_EXCEEDED",
                    "Too many incorrect attempts. Request a new code.");
        }
        if (!Crypto.constantTimeEquals(token.getCodeHash(), Crypto.sha256Hex(request.code().trim()))) {
            token.setAttempts(token.getAttempts() + 1);
            otpTokenRepository.save(token);
            throw ApiException.badRequest("Incorrect verification code");
        }
        token.setConsumed(true);
        otpTokenRepository.save(token);

        if (PURPOSE_EMAIL.equals(purpose)) user.setEmailVerified(true);
        if (PURPOSE_PHONE.equals(purpose)) user.setPhoneVerified(true);
        userRepository.save(user);
        auditService.recordForUser(user.getId(), user.getRole().name(), "OTP_VERIFIED", "User", user.getId(), purpose);
        return issueTokens(user, null);
    }

    @Transactional
    public String forgotPassword(String email) {
        User user = userRepository.findByEmailIgnoreCase(email.trim()).orElse(null);
        if (user == null) {
            // Do not disclose account existence.
            return null;
        }
        passwordResetTokenRepository.deleteByUserId(user.getId());
        String raw = Crypto.randomToken(32);
        PasswordResetToken token = new PasswordResetToken();
        token.setUserId(user.getId());
        token.setTokenHash(Crypto.sha256Hex(raw));
        token.setExpiresAt(Instant.now().plus(30, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(token);

        String link = properties.getFrontendBaseUrl() + "/reset-password?token=" + raw;
        emailSender.send(user.getEmail(), "[MediDrop] Reset your password",
                "Use this link within 30 minutes to reset your password: " + link);
        auditService.recordForUser(user.getId(), user.getRole().name(), "PASSWORD_RESET_REQUESTED", "User",
                user.getId(), null);
        return properties.getOtp().isExposeInResponse() ? raw : null;
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(Crypto.sha256Hex(rawToken))
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset link"));
        if (token.isUsed() || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Invalid or expired reset link");
        }
        User user = userRepository.findById(token.getUserId())
                .orElseThrow(() -> ApiException.notFound("Account not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setLocked(false);
        userRepository.save(user);
        token.setUsed(true);
        passwordResetTokenRepository.save(token);
        refreshTokenRepository.revokeAllForUser(user.getId());
        auditService.recordForUser(user.getId(), user.getRole().name(), "PASSWORD_RESET", "User", user.getId(), null);
        emailSender.send(user.getEmail(), "[MediDrop] Password changed",
                "Your MediDrop password was changed. If this was not you, contact support immediately.");
    }

    private String normalisePurpose(String purpose) {
        String value = purpose == null ? PURPOSE_EMAIL : purpose.trim().toUpperCase();
        return switch (value) {
            case PURPOSE_EMAIL, PURPOSE_PHONE, PURPOSE_LOGIN -> value;
            default -> throw ApiException.badRequest("Unsupported verification purpose");
        };
    }

    private String createOtp(User user, String purpose, boolean deliver) {
        otpTokenRepository.deleteByUserIdAndPurpose(user.getId(), purpose);
        String code = Crypto.numericCode(properties.getOtp().getLength());
        OtpToken token = new OtpToken();
        token.setUserId(user.getId());
        token.setPurpose(purpose);
        token.setCodeHash(Crypto.sha256Hex(code));
        token.setExpiresAt(Instant.now().plus(properties.getOtp().getExpiryMinutes(), ChronoUnit.MINUTES));
        otpTokenRepository.save(token);

        if (deliver) {
            if (PURPOSE_EMAIL.equals(purpose) || PURPOSE_LOGIN.equals(purpose)) {
                emailSender.send(user.getEmail(), "[MediDrop] Your verification code",
                        "Your MediDrop verification code is " + code + ". It expires in "
                                + properties.getOtp().getExpiryMinutes() + " minutes.");
            } else if (PURPOSE_PHONE.equals(purpose) && user.getPhone() != null) {
                smsSender.send(user.getPhone(), "Your MediDrop verification code is " + code);
            }
        }
        return code;
    }

    private AuthDtos.AuthResponse issueTokens(User user, String devOtp) {
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getEmail(), user.getRole());
        String rawRefresh = Crypto.randomToken(48);
        RefreshToken refresh = new RefreshToken();
        refresh.setUserId(user.getId());
        refresh.setTokenHash(Crypto.sha256Hex(rawRefresh));
        refresh.setExpiresAt(Instant.now().plus(properties.getJwt().getRefreshTokenDays(), ChronoUnit.DAYS));
        refreshTokenRepository.save(refresh);

        AuthDtos.AuthUser dto = new AuthDtos.AuthUser(user.getId(), user.getFullName(), user.getEmail(),
                user.getPhone(), user.getRole(), user.isEmailVerified(), user.isPhoneVerified());
        return new AuthDtos.AuthResponse(accessToken, rawRefresh, jwtService.accessTokenSeconds(), dto, devOtp);
    }
}
