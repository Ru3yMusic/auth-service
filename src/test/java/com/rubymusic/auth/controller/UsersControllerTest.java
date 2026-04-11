package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.exception.GlobalExceptionHandler;
import com.rubymusic.auth.mapper.UserMapper;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UsersControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private HttpServletRequest httpRequest;

    @InjectMocks
    private UsersController controller;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateProfile_matchingIds_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = mock(User.class);

        when(httpRequest.getHeader("X-User-Id")).thenReturn(userId.toString());
        when(userService.updateProfile(eq(userId), any(), any())).thenReturn(mockUser);
        when(userMapper.toDto(mockUser)).thenReturn(new UserResponse());

        mockMvc.perform(patch("/api/v1/auth/users/{id}/profile", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Test Name\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_mismatchedIds_returns403() throws Exception {
        UUID pathId   = UUID.randomUUID();
        UUID headerId = UUID.randomUUID();

        when(httpRequest.getHeader("X-User-Id")).thenReturn(headerId.toString());

        mockMvc.perform(patch("/api/v1/auth/users/{id}/profile", pathId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Test Name\"}"))
                .andExpect(status().isForbidden());
    }
}
