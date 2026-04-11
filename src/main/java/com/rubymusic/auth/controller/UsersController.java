package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.BatchUsersRequest;
import com.rubymusic.auth.dto.ChangeUserStatusRequest;
import com.rubymusic.auth.dto.UpdateProfileRequest;
import com.rubymusic.auth.dto.UserPage;
import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.dto.UserStatsDto;
import com.rubymusic.auth.dto.UserStatsResponse;
import com.rubymusic.auth.exception.ForbiddenException;
import com.rubymusic.auth.mapper.UserMapper;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UsersController implements UsersApi {

    private final UserService userService;
    private final UserMapper userMapper;
    private final HttpServletRequest httpRequest;

    @Override
    public ResponseEntity<UserPage> listUsers(String q, UserStatus status, Integer page, Integer size) {
        var p = userService.listUsers(q, status, PageRequest.of(page, size));
        UserPage dto = new UserPage()
                .content(userMapper.toDtoList(p.getContent()))
                .totalElements((int) p.getTotalElements())
                .totalPages(p.getTotalPages())
                .page(p.getNumber())
                .size(p.getSize());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<UserStatsResponse> getUserStats() {
        UserStatsDto stats = userService.getStats();
        UserStatsResponse response = new UserStatsResponse()
                .total(stats.totalUsers())
                .byGender(stats.byGender())
                .byStatus(stats.byStatus());
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> getUserById(UUID id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @Override
    public ResponseEntity<List<UserResponse>> batchGetUsers(BatchUsersRequest body) {
        List<User> users = userService.findByIds(body.getIds());
        return ResponseEntity.ok(userMapper.toDtoList(users));
    }

    @Override
    public ResponseEntity<UserResponse> changeUserStatus(UUID id, ChangeUserStatusRequest body) {
        BlockReason reason = body.getBlockReason() != null
                ? BlockReason.valueOf(body.getBlockReason().name()) : null;
        User user = userService.changeStatus(id, body.getStatus(), reason);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @Override
    public ResponseEntity<UserResponse> updateProfile(UUID id, UpdateProfileRequest body) {
        UUID requestingUserId = UUID.fromString(httpRequest.getHeader("X-User-Id"));
        if (!id.equals(requestingUserId)) {
            throw new ForbiddenException("Cannot update another user's profile");
        }
        User user = userService.updateProfile(
                id,
                body.getDisplayName(),
                body.getProfilePhotoUrl()
        );
        return ResponseEntity.ok(userMapper.toDto(user));
    }
}
