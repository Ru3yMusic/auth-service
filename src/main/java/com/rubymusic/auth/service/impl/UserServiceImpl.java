package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

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
        String queryParam = (q != null && q.isBlank()) ? null : q;
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
        return userRepository.save(user);
    }

    @Override
    public Map<String, Object> getStats() {
        long total = userRepository.count();

        Map<String, Long> byGender = new LinkedHashMap<>();
        for (Object[] row : userRepository.countByGender()) {
            byGender.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (Object[] row : userRepository.countByStatus()) {
            byStatus.put(row[0].toString(), (Long) row[1]);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("byGender", byGender);
        stats.put("byStatus", byStatus);
        stats.put("recentUsers", userRepository.findTop10ByOrderByCreatedAtDesc());
        return stats;
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
