package com.rubymusic.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless REST security config for auth-service.
 *
 * <p>Authorization strategy:
 * <ul>
 *   <li>{@code POST .../service-token} — open; secret validated inside the controller</li>
 *   <li>{@code /api/internal/v1/**} (other endpoints) — requires {@code ROLE_SERVICE} JWT</li>
 *   <li>{@code /api/v1/auth/internal/**} (legacy, during migration) — same rules as above</li>
 *   <li>All public auth routes ({@code /api/v1/auth/**}) — permit all</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   InternalJwtAuthFilter jwtAuthFilter) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        // Return 401 (not 302 redirect) for unauthenticated requests
                        .authenticationEntryPoint((req, res, authEx) ->
                                res.sendError(401, "Unauthorized"))
                        // Return 403 for authenticated users with insufficient role
                        .accessDeniedHandler((req, res, accessEx) ->
                                res.sendError(403, "Forbidden"))
                )
                .authorizeHttpRequests(auth -> auth
                        // service-token endpoints: open — credential check is done in the controller
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/internal/v1/auth/service-token",
                                "/api/v1/auth/internal/service-token",
                                "/api/internal/v1/service-token"
                        ).permitAll()

                        // All other internal paths require a service JWT
                        .requestMatchers(
                                "/api/internal/v1/**",
                                "/api/v1/auth/internal/**"
                        ).hasAuthority("ROLE_SERVICE")

                        // Public auth routes (registration, login, password recovery, etc.)
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
