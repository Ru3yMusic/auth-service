package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.dto.UserStatsDto;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.TokenService;
import com.rubymusic.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final TokenService tokenService;   // TASK-15: needed for session revocation on block

    @Override
    public User findById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Override
    public User findByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public Page<User> listUsers(String q, UserStatus status, Pageable pageable) {
        // Never pass null to the repo — the IS NULL check on a null-typed binding causes
        // PostgreSQL to infer bytea and throw "function lower(bytea) does not exist".
        // Empty string is the sentinel for "no filter"; the query checks (:q = '') instead.
        String queryParam = (q == null || q.isBlank()) ? "" : q;
        return userRepository.findByFilters(queryParam, status, pageable);
    }

    @Override
    public List<User> findByIds(List<UUID> ids) {
        return userRepository.findAllById(ids);
    }

    @Override
    @Transactional
    public User changeStatus(UUID userId, UserStatus newStatus, BlockReason blockReason) {
        if (newStatus == UserStatus.BLOCKED && blockReason == null) {
            throw new IllegalArgumentException("blockReason is required when status is BLOCKED");
        }
        User user = findById(userId);
        user.setStatus(newStatus);
        user.setBlockReason(newStatus == UserStatus.BLOCKED ? blockReason : null);
        User saved = userRepository.save(user);

        // TASK-15: immediately revoke all sessions when a user is blocked
        if (newStatus == UserStatus.BLOCKED) {
            tokenService.logoutAll(userId);
            log.info("All sessions revoked for blocked user: {}", userId);
        }

        return saved;
    }

    @Override
    public UserStatsDto getStats() {
        long total = userRepository.count();

        Map<String, Long> byGender = new LinkedHashMap<>();
        for (Object[] row : userRepository.countByGender()) {
            byGender.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : userRepository.countByStatus()) {
            byStatus.put(row[0].toString(), (Long) row[1]);
        }

        long activeUsers = byStatus.getOrDefault("ACTIVE", 0L);
        long blockedUsers = byStatus.getOrDefault("BLOCKED", 0L);

        log.debug("Stats computed: total={}, active={}, blocked={}", total, activeUsers, blockedUsers);
        return new UserStatsDto(total, activeUsers, blockedUsers, byGender, byStatus);
    }

    @Override
    @Transactional
    public User updateProfile(UUID userId, String displayName, String profilePhotoUrl) {
        User user = findById(userId);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName);
        }
        if (profilePhotoUrl != null) {
            user.setProfilePhotoUrl(profilePhotoUrl);
        }
        return userRepository.save(user);
    }
}
