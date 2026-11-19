package com.medidrop.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medidrop.common.ApiResponse;
import com.medidrop.config.MedidropProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Lightweight per-instance limiter for credential and OTP endpoints. For multi-instance
 * deployments replace the backing map with Redis (see docs/operations).
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT = 20;
    private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final MedidropProperties properties;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/auth/") || path.startsWith("/api/v1/payments/webhook"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = clientIp(request) + ":" + request.getRequestURI();
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= LIMIT) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                objectMapper.writeValue(response.getWriter(),
                        ApiResponse.error("Too many attempts. Please try again shortly."));
                return;
            }
            window.addLast(now);
        }
        if (hits.size() > 5_000) {
            // Drop only windows that have fully expired; clearing the map would reset active limits.
            hits.entrySet().removeIf(entry -> {
                Deque<Long> candidate = entry.getValue();
                synchronized (candidate) {
                    return candidate.isEmpty() || now - candidate.peekLast() > WINDOW_MS;
                }
            });
        }
        chain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (properties.getSecurity().isTrustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                // The trusted proxy appends the real peer last; earlier entries are client-controlled.
                String[] chain = forwarded.split(",");
                return chain[chain.length - 1].trim();
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.isBlank()) {
                return realIp.trim();
            }
        }
        return request.getRemoteAddr();
    }
}
