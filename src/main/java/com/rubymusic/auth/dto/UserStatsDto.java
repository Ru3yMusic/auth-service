package com.rubymusic.auth.dto;

import java.util.Map;

public record UserStatsDto(
        long totalUsers,
        long activeUsers,
        long blockedUsers,
        Map<String, Long> byGender,
        Map<String, Long> byStatus
) {}
