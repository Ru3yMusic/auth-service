package com.rubymusic.auth.config;

import com.rubymusic.auth.service.ServiceTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PlaylistServiceTokenCache.
 * Verifies local token generation, caching, and thread-safety.
 */
@ExtendWith(MockitoExtension.class)
class PlaylistServiceTokenCacheTest {

    @Mock
    private ServiceTokenGenerator serviceTokenGenerator;

    @InjectMocks
    private PlaylistServiceTokenCache tokenCache;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(tokenCache, "serviceName", "auth-service");
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    void getToken_callsGeneratorOnFirstCall_andReturnsToken() {
        when(serviceTokenGenerator.generateServiceToken("auth-service")).thenReturn("token-abc");

        String token = tokenCache.getToken();

        assertThat(token).isEqualTo("token-abc");
        verify(serviceTokenGenerator, times(1)).generateServiceToken("auth-service");
    }

    // ── Caching ───────────────────────────────────────────────────────────────

    @Test
    void getToken_returnsCachedToken_onSubsequentCalls() {
        // TRIANGULATE: second call must NOT hit the generator again
        when(serviceTokenGenerator.generateServiceToken("auth-service")).thenReturn("token-xyz");

        String first  = tokenCache.getToken();
        String second = tokenCache.getToken();

        assertThat(first).isEqualTo("token-xyz");
        assertThat(second).isEqualTo("token-xyz");
        verify(serviceTokenGenerator, times(1)).generateServiceToken("auth-service");
    }

    // ── Thread-safety ─────────────────────────────────────────────────────────

    @Test
    void concurrentGetToken_callsGenerateOnlyOnce() throws InterruptedException {
        when(serviceTokenGenerator.generateServiceToken(any())).thenReturn("concurrent-token");

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        ExecutorService executor  = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    tokenCache.getToken();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertThat(doneLatch.await(5, TimeUnit.SECONDS)).isTrue();
        executor.shutdown();

        verify(serviceTokenGenerator, times(1)).generateServiceToken("auth-service");
    }
}
