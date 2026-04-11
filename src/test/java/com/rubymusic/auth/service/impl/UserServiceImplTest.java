package com.rubymusic.auth.service.impl;

import com.rubymusic.auth.dto.UserStatsDto;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.repository.UserRepository;
import com.rubymusic.auth.service.TokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @InjectMocks
    private UserServiceImpl userService;

    // ── getStats ──────────────────────────────────────────────────────────────

    @Test
    void getStats_returnsUserStatsDto_withCorrectCounts() {
        when(userRepository.count()).thenReturn(100L);
        when(userRepository.countByGender()).thenReturn(List.<Object[]>of(
                new Object[]{"MALE", 60L},
                new Object[]{"FEMALE", 40L}
        ));
        when(userRepository.countByStatus()).thenReturn(List.<Object[]>of(
                new Object[]{"ACTIVE", 70L},
                new Object[]{"INACTIVE", 20L},
                new Object[]{"BLOCKED", 10L}
        ));

        UserStatsDto stats = userService.getStats();

        assertThat(stats.totalUsers()).isEqualTo(100L);
        assertThat(stats.activeUsers()).isEqualTo(70L);
        assertThat(stats.blockedUsers()).isEqualTo(10L);
        assertThat(stats.byGender())
                .containsEntry("MALE", 60L)
                .containsEntry("FEMALE", 40L);
        assertThat(stats.byStatus())
                .containsEntry("ACTIVE", 70L)
                .containsEntry("BLOCKED", 10L);
    }

    @Test
    void getStats_whenNoActiveOrBlockedUsers_returnsZero() {
        when(userRepository.count()).thenReturn(10L);
        when(userRepository.countByGender()).thenReturn(List.of());
        when(userRepository.countByStatus()).thenReturn(List.<Object[]>of(
                new Object[]{"INACTIVE", 10L}
        ));

        UserStatsDto stats = userService.getStats();

        assertThat(stats.totalUsers()).isEqualTo(10L);
        assertThat(stats.activeUsers()).isEqualTo(0L);
        assertThat(stats.blockedUsers()).isEqualTo(0L);
        assertThat(stats.byGender()).isEmpty();
    }

    // ── changeStatus — TASK-15 ────────────────────────────────────────────────

    @Test
    void changeStatus_BLOCKED_revokesAllUserSessions() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.changeStatus(userId, UserStatus.BLOCKED, BlockReason.SPAM_OR_ADVERTISING);

        // TASK-15: blocking a user must immediately revoke all their sessions
        verify(tokenService).logoutAll(userId);
    }

    @Test
    void changeStatus_ACTIVE_doesNotRevokeTokens() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .status(UserStatus.BLOCKED)
                .blockReason(BlockReason.SPAM_OR_ADVERTISING)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.changeStatus(userId, UserStatus.ACTIVE, null);

        verify(tokenService, never()).logoutAll(any());
    }

    @Test
    void changeStatus_INACTIVE_doesNotRevokeTokens() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .status(UserStatus.ACTIVE)
                .build();
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        userService.changeStatus(userId, UserStatus.INACTIVE, null);

        verify(tokenService, never()).logoutAll(any());
    }
}
