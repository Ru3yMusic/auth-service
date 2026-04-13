package com.rubymusic.auth.config;

import com.rubymusic.auth.service.ServiceTokenGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Caches the service JWT used by auth-service when calling playlist-service.
 *
 * <p>Auth-service generates tokens locally (it holds the RSA private key) —
 * no external auth-service call needed. Token is refreshed automatically
 * 1 minute before expiry using double-checked locking on volatile fields.
 */
@Component
@RequiredArgsConstructor
public class PlaylistServiceTokenCache {

    private final ServiceTokenGenerator serviceTokenGenerator;

    @Value("${spring.application.name}")
    private String serviceName;

    private volatile String cachedToken;
    private volatile long tokenExpiresAt;

    public String getToken() {
        if (cachedToken == null || System.currentTimeMillis() > tokenExpiresAt - 60_000) {
            synchronized (this) {
                if (cachedToken == null || System.currentTimeMillis() > tokenExpiresAt - 60_000) {
                    cachedToken = serviceTokenGenerator.generateServiceToken(serviceName);
                    tokenExpiresAt = System.currentTimeMillis() + ServiceTokenGenerator.SERVICE_TOKEN_TTL_MS;
                }
            }
        }
        return cachedToken;
    }
}
