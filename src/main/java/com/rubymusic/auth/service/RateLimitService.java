package com.rubymusic.auth.service;

import com.rubymusic.auth.exception.RateLimitExceededException;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RateLimitService {

    @Value("${auth.rate-limit.max-requests:5}")
    private int maxRequests;

    @Value("${auth.rate-limit.window-minutes:15}")
    private int windowMinutes;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Checks whether the given key is within its allowed rate limit window.
     * Creates a new bucket on first access. Throws RateLimitExceededException
     * when all tokens are consumed.
     */
    public void checkRateLimit(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, this::newBucket);
        if (!bucket.tryConsume(1)) {
            log.warn("Rate limit exceeded for key={}", key);
            throw new RateLimitExceededException("Rate limit exceeded — try again later");
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private Bucket newBucket(String key) {
        Bandwidth limit = Bandwidth.classic(
                maxRequests,
                Refill.intervally(maxRequests, Duration.ofMinutes(windowMinutes))
        );
        return Bucket.builder().addLimit(limit).build();
    }
}
