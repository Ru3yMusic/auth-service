package com.rubymusic.auth.repository;

import com.rubymusic.auth.model.EmailVerification;
import com.rubymusic.auth.model.enums.VerificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailVerificationRepository extends JpaRepository<EmailVerification, UUID> {

    /** Returns the most recent unused OTP for the given email + type */
    Optional<EmailVerification> findTopByEmailAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(
            String email, VerificationType type);

    /** Scheduled cleanup: removes expired records to keep the table lean */
    @Modifying
    @Query("DELETE FROM EmailVerification ev WHERE ev.expiresAt < :threshold")
    void deleteExpiredVerifications(LocalDateTime threshold);
}
