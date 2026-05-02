package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.UserResponse;
import com.rubymusic.auth.dto.UserStatsDto;
import com.rubymusic.auth.exception.GlobalExceptionHandler;
import com.rubymusic.auth.mapper.UserMapper;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.BlockReason;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ── listUsers ─────────────────────────────────────────────────────────────

    @Test
    void listUsers_returns200_withMappedPage() throws Exception {
        User u1 = mock(User.class);
        User u2 = mock(User.class);
        Page<User> page = new PageImpl<>(List.of(u1, u2), PageRequest.of(0, 20), 2);

        when(userService.listUsers(eq("alice"), eq(UserStatus.ACTIVE), any(PageRequest.class)))
                .thenReturn(page);
        when(userMapper.toDtoList(anyList()))
                .thenReturn(List.of(new UserResponse(), new UserResponse()));

        mockMvc.perform(get("/api/v1/auth/users")
                        .param("q", "alice")
                        .param("status", "ACTIVE")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    // ── getUserStats ──────────────────────────────────────────────────────────

    @Test
    void getUserStats_returns200_withCounts() throws Exception {
        UserStatsDto stats = new UserStatsDto(
                100L, 80L, 5L,
                Map.of("MASCULINO", 60L, "FEMENINO", 40L),
                Map.of("ACTIVE", 80L, "BLOCKED", 5L)
        );
        when(userService.getStats()).thenReturn(stats);

        mockMvc.perform(get("/api/v1/auth/users/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(100))
                .andExpect(jsonPath("$.byGender.MASCULINO").value(60))
                .andExpect(jsonPath("$.byStatus.ACTIVE").value(80));
    }

    // ── getUserById ───────────────────────────────────────────────────────────

    @Test
    void getUserById_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userService.findById(userId)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(new UserResponse());

        mockMvc.perform(get("/api/v1/auth/users/{id}", userId))
                .andExpect(status().isOk());

        verify(userService).findById(userId);
    }

    @Test
    void getUserById_notFound_returns404() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userService.findById(userId))
                .thenThrow(new NoSuchElementException("User not found"));

        mockMvc.perform(get("/api/v1/auth/users/{id}", userId))
                .andExpect(status().isNotFound());
    }

    // ── batchGetUsers ─────────────────────────────────────────────────────────

    @Test
    void batchGetUsers_returns200_mappedList() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        when(userService.findByIds(anyList())).thenReturn(List.of(mock(User.class)));
        when(userMapper.toDtoList(anyList())).thenReturn(List.of(new UserResponse()));

        mockMvc.perform(post("/api/v1/auth/users/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"" + id1 + "\",\"" + id2 + "\"]}"))
                .andExpect(status().isOk());
    }

    // ── changeUserStatus ──────────────────────────────────────────────────────

    @Test
    void changeUserStatus_block_withReason_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userService.changeStatus(eq(userId), eq(UserStatus.BLOCKED), eq(BlockReason.HARASSMENT_OR_BULLYING)))
                .thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(new UserResponse());

        mockMvc.perform(patch("/api/v1/auth/users/{id}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"BLOCKED\",\"blockReason\":\"HARASSMENT_OR_BULLYING\"}"))
                .andExpect(status().isOk());

        verify(userService).changeStatus(userId, UserStatus.BLOCKED, BlockReason.HARASSMENT_OR_BULLYING);
    }

    @Test
    void changeUserStatus_unblock_noReason_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        when(userService.changeStatus(eq(userId), eq(UserStatus.ACTIVE), eq(null)))
                .thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(new UserResponse());

        mockMvc.perform(patch("/api/v1/auth/users/{id}/status", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        verify(userService).changeStatus(userId, UserStatus.ACTIVE, null);
    }

    // ── updateProfile ─────────────────────────────────────────────────────────

    @Test
    void updateProfile_matchingHeader_returns200() throws Exception {
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
    void updateProfile_matchingSecurityContextPrincipal_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = mock(User.class);

        // SecurityContext authenticated with the userId as principal name
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(userService.updateProfile(eq(userId), any(), any())).thenReturn(mockUser);
        when(userMapper.toDto(mockUser)).thenReturn(new UserResponse());

        mockMvc.perform(patch("/api/v1/auth/users/{id}/profile", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Test Name\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void updateProfile_anonymousPrincipal_fallsBackToHeader_returns200() throws Exception {
        UUID userId = UUID.randomUUID();
        User mockUser = mock(User.class);

        var anon = new AnonymousAuthenticationToken(
                "key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anon);
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

    @Test
    void updateProfile_noPrincipalNoHeader_returns401() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn(null);

        mockMvc.perform(patch("/api/v1/auth/users/{id}/profile", pathId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Test Name\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_blankHeader_returns401() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn("   ");

        mockMvc.perform(patch("/api/v1/auth/users/{id}/profile", pathId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Test Name\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateProfile_invalidUuidHeader_returns401() throws Exception {
        UUID pathId = UUID.randomUUID();
        when(httpRequest.getHeader("X-User-Id")).thenReturn("not-a-uuid");

        mockMvc.perform(patch("/api/v1/auth/users/{id}/profile", pathId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Test Name\"}"))
                .andExpect(status().isUnauthorized());
    }
}
