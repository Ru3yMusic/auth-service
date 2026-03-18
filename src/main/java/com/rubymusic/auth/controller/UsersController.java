package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.UpdateProfileRequest;
import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.mapper.UserMapper;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UsersController implements UsersApi {

    private final UserService userService;
    private final UserMapper userMapper;

    @Override
    public ResponseEntity<UserResponse> getUserById(UUID id) {
        User user = userService.findById(id);
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
