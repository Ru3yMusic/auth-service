package com.rubymusic.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    /**
     * Provides in-memory RSA keys so that JwtConfig's @ConditionalOnMissingBean
     * skips its property-based bean creation (which would fail with no jwt.*
     * properties configured in application-test.yml).
     */
    @TestConfiguration
    static class TestKeyConfig {

        private static final KeyPair KEY_PAIR;

        static {
            try {
                KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
                gen.initialize(2048, new SecureRandom());
                KEY_PAIR = gen.generateKeyPair();
            } catch (Exception e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        @Bean
        @Primary
        public PublicKey jwtPublicKey() {
            return KEY_PAIR.getPublic();
        }

        @Bean
        @Primary
        public PrivateKey jwtPrivateKey() {
            return KEY_PAIR.getPrivate();
        }
    }

    @Test
    void contextLoads() {
    }
}
