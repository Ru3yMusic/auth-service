package com.rubymusic.auth.service;

import com.rubymusic.auth.dto.UserStatsDto;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface UserService {

    User findById(UUID id);

    User findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Admin user list — search by display name / email, optionally filter by status.
     * Null parameters are treated as "no filter".
     */
    Page<User> listUsers(String q, UserStatus status, Pageable pageable);

    /**
     * Fetches multiple users by ID in one query.
     * Used for the Amigos / friends list screen to resolve display names in bulk.
     * Unknown IDs are silently omitted from the result.
     */
    List<User> findByIds(List<UUID> ids);

    /**
     * Admin action: change a user's status. blockReason is required when status = BLOCKED,
     * and is cleared automatically when moving to any other status.
     */
    User changeStatus(UUID userId, UserStatus newStatus, BlockReason blockReason);

    /**
     * Returns aggregate counts for the admin dashboard.
     * Keys: "total", "byGender" (map), "byStatus" (map).
     */
    UserStatsDto getStats();

    /**
     * Updates mutable profile fields. Null values are ignored (partial update).
     */
    User updateProfile(UUID userId, String displayName, String profilePhotoUrl);
}
