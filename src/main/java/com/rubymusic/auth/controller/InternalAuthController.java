package com.rubymusic.auth.controller;

import com.rubymusic.auth.dto.ServiceTokenResponse;
import com.rubymusic.auth.service.ServiceTokenGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint for service-to-service token exchange (zero-trust M2M).
 * Not exposed through the public OpenAPI contract.
 * Called by: interaction-service (and any future service needing internal access).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth/internal")
public class InternalAuthController {

    private final ServiceTokenGenerator serviceTokenGenerator;

    @Value("${internal.service-secret}")
    private String internalServiceSecret;

    @PostMapping("/service-token")
    public ResponseEntity<ServiceTokenResponse> getServiceToken(
            @RequestHeader("X-Service-Name") String serviceName,
            @RequestHeader("X-Service-Secret") String serviceSecret) {

        if (!internalServiceSecret.equals(serviceSecret)) {
            log.warn("Rejected service-token request from: {} (invalid secret)", serviceName);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String token = serviceTokenGenerator.generateServiceToken(serviceName);
        log.debug("Issued service token for: {}", serviceName);
        return ResponseEntity.ok(new ServiceTokenResponse(token, ServiceTokenGenerator.SERVICE_TOKEN_TTL_MS / 1000));
    }
}
