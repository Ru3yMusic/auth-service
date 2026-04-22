package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.ServiceTokenRequest;
import com.rubymusic.auth.dto.ServiceTokenResponse;
import com.rubymusic.auth.dto.UserInternalDto;
import com.rubymusic.auth.exception.UnauthorizedException;
import com.rubymusic.auth.model.User;
import com.rubymusic.auth.service.ServiceTokenGenerator;
import com.rubymusic.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Internal endpoints for service-to-service interactions (zero-trust M2M).
 * Not exposed through the public API gateway — blocked at gateway level.
 *
 * <p>Supports dual base path during migration:
 * <ul>
 *   <li>Legacy: {@code /api/v1/auth/internal} (kept until all callers migrate)</li>
 *   <li>New:    {@code /api/internal/v1/auth} (canonical zero-trust path)</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/auth/internal", "/api/internal/v1/auth", "/api/internal/v1"})
public class InternalAuthController {

    private final ServiceTokenGenerator serviceTokenGenerator;
    private final UserService userService;

    @Value("${internal.service-secret}")
    private String internalServiceSecret;

    /**
     * Issues a service JWT for M2M authentication.
     *
     * <p>Supports two credential formats for backward compatibility:
     * <ul>
     *   <li><b>New (body-based)</b>: {@code ServiceTokenRequest} JSON body</li>
     *   <li><b>Legacy (header-based)</b>: {@code X-Service-Name} + {@code X-Service-Secret} headers</li>
     * </ul>
     * Body takes precedence when both are present.
     */
    @PostMapping("/service-token")
    public ResponseEntity<ServiceTokenResponse> getServiceToken(
            @RequestBody(required = false) ServiceTokenRequest body,
            @RequestHeader(value = "X-Service-Name", required = false) String nameHeader,
            @RequestHeader(value = "X-Service-Secret", required = false) String secretHeader) {

        String serviceName = (body != null) ? body.getServiceName() : nameHeader;
        String serviceSecret = (body != null) ? body.getServiceSecret() : secretHeader;

        if (serviceSecret == null || !MessageDigest.isEqual(
                internalServiceSecret.getBytes(StandardCharsets.UTF_8),
                serviceSecret.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Rejected service-token request from: {} (invalid secret)", serviceName);
            throw new UnauthorizedException("Invalid service secret");
        }

        String token = serviceTokenGenerator.generateServiceToken(serviceName);
        log.debug("Issued service token for: {}", serviceName);

        ServiceTokenResponse response = new ServiceTokenResponse()
                .token(token)
                .expiresIn((int) (ServiceTokenGenerator.SERVICE_TOKEN_TTL_MS / 1000));
        return ResponseEntity.ok(response);
    }

    /**
     * Returns minimal user data for internal cross-service consumption.
     * Only fields needed by consumer services are exposed (not the full UserResponse).
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<UserInternalDto> getUserById(@PathVariable UUID id) {
        User user = userService.findById(id);
        return ResponseEntity.ok(toInternalDto(user));
    }

    /**
     * Batch fetch by comma-separated UUIDs. Unknown IDs are silently omitted.
     */
    @GetMapping("/users/batch")
    public ResponseEntity<List<UserInternalDto>> getUsersBatch(@RequestParam String ids) {
        List<UUID> uuidList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UUID::fromString)
                .collect(Collectors.toList());

        List<UserInternalDto> result = userService.findByIds(uuidList).stream()
                .map(this::toInternalDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private UserInternalDto toInternalDto(User user) {
        UserInternalDto dto = new UserInternalDto()
                .id(user.getId())
                .email(user.getEmail())
                .username(user.getDisplayName())
                .profilePhotoUrl(user.getProfilePhotoUrl());

        if (user.getStatus() != null) {
            dto.status(UserInternalDto.StatusEnum.fromValue(user.getStatus().name()));
        }
        return dto;
    }
}
