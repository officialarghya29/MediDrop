package com.medidrop.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    /**
     * Locks the newest unconsumed code so two simultaneous verifies cannot both mint tokens.
     * The caller must keep the surrounding transaction open until the code is marked consumed.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select t from OtpToken t
            where t.userId = :userId and t.purpose = :purpose and t.consumed = false
            order by t.id desc
            """)
    List<OtpToken> findActiveForUpdate(@Param("userId") Long userId,
                                       @Param("purpose") String purpose,
                                       Pageable pageable);

    void deleteByUserIdAndPurpose(Long userId, String purpose);
}
