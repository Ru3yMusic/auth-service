package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.BatchUsersRequest;
import com.rubymusic.auth.dto.ChangeUserStatusRequest;
import com.rubymusic.auth.dto.UpdateProfileRequest;
import com.rubymusic.auth.dto.UserPage;
import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.mapper.UserMapper;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UsersController implements UsersApi {

    private final UserService userService;
    private final UserMapper userMapper;

    @Override
    public ResponseEntity<UserPage> listUsers(String q, String status, Integer page, Integer size) {
        UserStatus statusEnum = (status != null) ? UserStatus.valueOf(status) : null;
        var p = userService.listUsers(q, statusEnum, PageRequest.of(page, size));
        UserPage dto = new UserPage()
                .content(userMapper.toDtoList(p.getContent()))
                .totalElements((int) p.getTotalElements())
                .totalPages(p.getTotalPages())
                .page(p.getNumber())
                .size(p.getSize());
        return ResponseEntity.ok(dto);
    }

    @Override
    public ResponseEntity<Map<String, Object>> getUserStats() {
        return ResponseEntity.ok(userService.getStats());
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
        User user = userService.changeStatus(id, UserStatus.valueOf(body.getStatus().name()), reason);
        return ResponseEntity.ok(userMapper.toDto(user));
    }

    @Override
    public ResponseEntity<UserResponse> updateProfile(UUID id, UpdateProfileRequest body) {
        User user = userService.updateProfile(
                id,
                body.getDisplayName(),
                body.getProfilePhotoUrl()
        );
        return ResponseEntity.ok(userMapper.toDto(user));
    }
}
