package com.rubymusic.auth.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.List;

/**
 * Extracts and validates RS256 JWTs from the {@code Authorization: Bearer} header.
 * Maps the {@code role} claim to a Spring Security authority ({@code ROLE_SERVICE} or {@code ROLE_USER}).
 *
 * <p>On a missing or invalid token the filter does NOT reject the request — it simply
 * does not set authentication in the SecurityContext. The authorization rules in
 * {@link SecurityConfig} decide what happens next (401 for missing auth, 403 for wrong role).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InternalJwtAuthFilter extends OncePerRequestFilter {

    private final PublicKey jwtPublicKey;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token != null) {
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(jwtPublicKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String role = claims.get("role", String.class);
                String authority = "ROLE_" + (role != null ? role : "USER");

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        claims.getSubject(),
                        null,
                        List.of(new SimpleGrantedAuthority(authority))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("JWT authenticated: sub={} role={}", claims.getSubject(), role);

            } catch (JwtException e) {
                // Invalid/expired token — clear any stale context; authorization rules return 401
                SecurityContextHolder.clearContext();
                log.debug("JWT validation failed: {}", e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
