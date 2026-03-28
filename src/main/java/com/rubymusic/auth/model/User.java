package com.rubymusic.auth.model;

import com.rubymusic.auth.model.enums.AuthProvider;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.Gender;
import com.rubymusic.auth.model.enums.UserRole;
import com.rubymusic.auth.model.enums.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_google_id", columnList = "google_id")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    /** Null when auth_provider = GOOGLE */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 10)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.EMAIL;

    @Column(name = "google_id", unique = true, length = 255)
    private String googleId;

    /** URL stored in cloud storage — never binary in DB */
    @Column(name = "profile_photo_url", columnDefinition = "TEXT")
    private String profilePhotoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    /** Populated only when status = BLOCKED */
    @Enumerated(EnumType.STRING)
    @Column(name = "block_reason", length = 35)
    private BlockReason blockReason;

    @Column(name = "is_email_verified", nullable = false)
    @Builder.Default
    private Boolean isEmailVerified = false;

    @Column(name = "accepts_marketing", nullable = false)
    @Builder.Default
    private Boolean acceptsMarketing = false;

    @Column(name = "accepts_data_sharing", nullable = false)
    @Builder.Default
    private Boolean acceptsDataSharing = false;

    @Column(name = "accepted_terms", nullable = false)
    @Builder.Default
    private Boolean acceptedTerms = false;

    @Column(name = "accepted_privacy_policy", nullable = false)
    @Builder.Default
    private Boolean acceptedPrivacyPolicy = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RefreshToken> refreshTokens = new ArrayList<>();
}
