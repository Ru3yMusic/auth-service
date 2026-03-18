package com.rubymusic.auth.service;

import com.rubymusic.auth.model.User;

import java.util.UUID;

public interface UserService {

    User findById(UUID id);

    User findByEmail(String email);

    boolean existsByEmail(String email);

    /**
     * Updates mutable profile fields. Null values are ignored (partial update).
     */
    User updateProfile(UUID userId, String displayName, String profilePhotoUrl);
}
