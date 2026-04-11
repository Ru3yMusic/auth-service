package com.rubymusic.auth.config;

import com.rubymusic.auth.service.ServiceTokenGenerator;
import feign.RequestInterceptor;
import feign.RequestTemplate;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceAuthFeignConfigTest {

    @Mock
    private ServiceTokenGenerator serviceTokenGenerator;

    @InjectMocks
    private ServiceAuthFeignConfig config;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(config, "serviceName", "auth-service");
    }

    @Test
    void concurrentTokenResolution_callsGenerateOnlyOnce() throws InterruptedException {
        when(serviceTokenGenerator.generateServiceToken(any())).thenReturn("test-token");

        int threadCount = 20;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        ExecutorService executor  = Executors.newFixedThreadPool(threadCount);

        RequestInterceptor interceptor = config.serviceAuthInterceptor();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    interceptor.apply(new RequestTemplate());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads should finish within 5 s");
        executor.shutdown();

        verify(serviceTokenGenerator, times(1)).generateServiceToken("auth-service");
    }
}
