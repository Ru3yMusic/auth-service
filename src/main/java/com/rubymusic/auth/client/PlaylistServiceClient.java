package com.rubymusic.auth.client;

import com.rubymusic.auth.config.ServiceAuthFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/**
 * Feign client for playlist-service internal endpoints.
 * Used by auth-service after email verification to create the system playlist.
 */
@FeignClient(name = "playlist-service", configuration = ServiceAuthFeignConfig.class)
public interface PlaylistServiceClient {

    @PostMapping("/api/v1/playlists/internal/system/{userId}")
    void createSystemPlaylist(@PathVariable("userId") UUID userId);
}
