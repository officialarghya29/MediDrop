package com.medidrop.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    Optional<OtpToken> findFirstByUserIdAndPurposeAndConsumedFalseOrderByCreatedAtDesc(Long userId, String purpose);

    void deleteByUserIdAndPurpose(Long userId, String purpose);
}
