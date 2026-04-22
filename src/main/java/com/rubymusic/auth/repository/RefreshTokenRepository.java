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

    /**
     * Bulk-revoke all active sessions for a user (logout-all / password change).
     *
     * NOTE: clearAutomatically is intentionally OFF. When it was true, any dirty
     * entity in the persistence context (e.g. a User whose status/block_reason
     * was just set in the same transaction) was detached by em.clear() BEFORE
     * the transaction committed, and its pending UPDATE was silently dropped.
     * That broke:
     *   - UserServiceImpl.changeStatus (admin block: status reverted to ACTIVE)
     *   - PasswordResetServiceImpl.resetPassword (new password hash discarded)
     * The bulk UPDATE here only touches refresh_tokens; leaving the context
     * untouched is correct and prevents the lost-update footgun.
     */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revokedAt = :now " +
           "WHERE rt.user.id = :userId AND rt.revokedAt IS NULL")
    void revokeAllByUserId(UUID userId, LocalDateTime now);

    /** Scheduled cleanup: removes expired and revoked tokens */
    @Modifying(clearAutomatically = true)
    @Query("DELETE FROM RefreshToken rt " +
           "WHERE rt.expiresAt < :threshold OR rt.revokedAt IS NOT NULL")
    void deleteExpiredAndRevoked(LocalDateTime threshold);
}
