package com.rubymusic.auth.repository;

import com.rubymusic.auth.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Bulk-revoke all active sessions for a user (logout-all / password change) */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now " +
           "WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    void revokeAllByUserId(UUID userId, LocalDateTime now);

    /** Scheduled cleanup: removes expired and revoked tokens */
    @Modifying
    @Query("DELETE FROM RefreshToken rt " +
           "WHERE rt.expiresAt < :threshold OR rt.revokedAt IS NOT NULL")
    void deleteExpiredAndRevoked(LocalDateTime threshold);
}
