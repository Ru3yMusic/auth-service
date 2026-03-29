package com.rubymusic.auth.config;

import com.rubymusic.auth.service.ServiceTokenGenerator;
import feign.RequestInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * Per-client Feign configuration that injects a service JWT into every outgoing request.
 * auth-service generates tokens locally (it holds the RSA private key) — no external call needed.
 *
 * Not annotated with @Configuration — registered only for specific Feign clients.
 */
@RequiredArgsConstructor
public class ServiceAuthFeignConfig {

    private final ServiceTokenGenerator serviceTokenGenerator;

    @Value("${spring.application.name}")
    private String serviceName;

    private volatile String cachedToken;
    private volatile long tokenExpiresAt;

    @Bean
    public RequestInterceptor serviceAuthInterceptor() {
        return template -> template.header("Authorization", "Bearer " + resolveToken());
    }

    private String resolveToken() {
        if (cachedToken == null || System.currentTimeMillis() > tokenExpiresAt - 60_000) {
            cachedToken = serviceTokenGenerator.generateServiceToken(serviceName);
            tokenExpiresAt = System.currentTimeMillis() + ServiceTokenGenerator.SERVICE_TOKEN_TTL_MS;
        }
        return cachedToken;
    }
}
