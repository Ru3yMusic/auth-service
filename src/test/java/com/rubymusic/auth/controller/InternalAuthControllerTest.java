package com.rubymusic.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rubymusic.auth.dto.ServiceTokenRequest;
import com.rubymusic.auth.dto.UserInternalDto;
import com.rubymusic.auth.exception.GlobalExceptionHandler;
import com.rubymusic.auth.exception.UserNotFoundException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.model.enums.UserStatus;
import com.rubymusic.auth.service.ServiceTokenGenerator;
import com.rubymusic.auth.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InternalAuthControllerTest {

    @Mock
    private ServiceTokenGenerator serviceTokenGenerator;

    @Mock
    private UserService userService;

    @InjectMocks
    private InternalAuthController controller;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private static final String VALID_SECRET = "correct-secret";
    private static final String SERVICE_NAME = "interaction-service";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "internalServiceSecret", VALID_SECRET);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    // ── Legacy path (headers) ──────────────────────────────────────────────────

    @Test
    void legacyPath_validSecret_returns200() throws Exception {
        when(serviceTokenGenerator.generateServiceToken(SERVICE_NAME)).thenReturn("jwt-token");

        mockMvc.perform(post("/api/v1/auth/internal/service-token")
                        .header("X-Service-Name", SERVICE_NAME)
                        .header("X-Service-Secret", VALID_SECRET))
                .andExpect(status().isOk());
    }

    @Test
    void legacyPath_invalidSecret_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/internal/service-token")
                        .header("X-Service-Name", SERVICE_NAME)
                        .header("X-Service-Secret", "wrong-secret"))
                .andExpect(status().isUnauthorized());
    }

    // ── New path (request body) ────────────────────────────────────────────────

    @Test
    void newPath_validBodySecret_returns200WithToken() throws Exception {
        when(serviceTokenGenerator.generateServiceToken(SERVICE_NAME)).thenReturn("svc-jwt");

        ServiceTokenRequest req = new ServiceTokenRequest(SERVICE_NAME, VALID_SECRET);
        mockMvc.perform(post("/api/internal/v1/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", is("svc-jwt")));
    }

    @Test
    void newPath_invalidBodySecret_returns401() throws Exception {
        ServiceTokenRequest req = new ServiceTokenRequest(SERVICE_NAME, "wrong");
        mockMvc.perform(post("/api/internal/v1/auth/service-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }

    // ── User lookup endpoints ──────────────────────────────────────────────────

    @Test
    void getUserById_existingUser_returns200WithDto() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("user@test.com")
                .displayName("Test User")
                .profilePhotoUrl("https://img.test/photo.jpg")
                .status(UserStatus.ACTIVE)
                .build();
        when(userService.findById(userId)).thenReturn(user);

        mockMvc.perform(get("/api/internal/v1/auth/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(userId.toString())))
                .andExpect(jsonPath("$.email", is("user@test.com")))
                .andExpect(jsonPath("$.username", is("Test User")))
                .andExpect(jsonPath("$.status", is("ACTIVE")));
    }

    @Test
    void getUserById_unknownUser_returns404() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(userService.findById(unknownId)).thenThrow(new UserNotFoundException("Not found"));

        mockMvc.perform(get("/api/internal/v1/auth/users/{id}", unknownId))
                .andExpect(status().isNotFound());
    }

    @Test
    void getUsersBatch_validIds_returnsOnlyExistingUsers() throws Exception {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        User user1 = User.builder()
                .id(id1)
                .email("a@test.com")
                .displayName("Alice")
                .status(UserStatus.ACTIVE)
                .build();
        User user2 = User.builder()
                .id(id2)
                .email("b@test.com")
                .displayName("Bob")
                .status(UserStatus.INACTIVE)
                .build();

        when(userService.findByIds(java.util.List.of(id1, id2)))
                .thenReturn(java.util.List.of(user1, user2));

        mockMvc.perform(get("/api/internal/v1/auth/users/batch")
                        .param("ids", id1 + "," + id2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(2)))
                .andExpect(jsonPath("$[0].username", is("Alice")))
                .andExpect(jsonPath("$[1].username", is("Bob")));
    }

    @Test
    void getUsersBatch_noMatchingIds_returnsEmptyList() throws Exception {
        UUID unknownId = UUID.randomUUID();
        when(userService.findByIds(java.util.List.of(unknownId)))
                .thenReturn(java.util.List.of());

        mockMvc.perform(get("/api/internal/v1/auth/users/batch")
                        .param("ids", unknownId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()", is(0)));
    }
}
