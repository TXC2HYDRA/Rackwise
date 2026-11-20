package com.capacitydesk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Lets Prometheus scrape /actuator/prometheus with a static bearer token (JWTs expire every 15 minutes, so a scraper
 * cannot use them). The token grants ONLY role METRICS, which only the Prometheus endpoint accepts. Disabled when unset.
 */
final class ScrapeTokenFilter extends OncePerRequestFilter {

    static final String PATH = "/actuator/prometheus";
    private final byte[] token;

    ScrapeTokenFilter(String token) {
        this.token = token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8);
    }

    /** True when this request carries the scrape token for the Prometheus path. */
    boolean matches(HttpServletRequest req) {
        if (token.length == 0 || !PATH.equals(req.getRequestURI())) return false;
        String h = req.getHeader("Authorization");
        return h != null && h.startsWith("Bearer ")
                && MessageDigest.isEqual(h.substring(7).getBytes(StandardCharsets.UTF_8), token);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        if (matches(req)) {
            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated("prometheus", null, List.of(new SimpleGrantedAuthority("ROLE_METRICS"))));
        }
        chain.doFilter(req, res);
    }
}
