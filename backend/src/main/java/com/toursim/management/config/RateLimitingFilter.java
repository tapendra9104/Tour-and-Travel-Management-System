package com.toursim.management.config;

import java.io.IOException;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Simple IP-based sliding-window rate limiter for sensitive endpoints.
 * Zero external dependencies - uses in-process ConcurrentHashMap.
 *
 * Limits (per IP):
 *   POST /login              -> 15 req / 60 s
 *   POST /register           -> 5  req / 60 s
 *   POST /forgot-password    -> 5  req / 60 s
 *   POST /api/bookings       -> 30 req / 60 s
 *
 * For high-traffic production use, replace with Bucket4j + Redis.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitingFilter implements Filter {

    private record Limit(int maxRequests, long windowMillis) {}

    private static final Map<String, Limit> ENDPOINT_LIMITS = Map.of(
        "/login",           new Limit(15,  60_000),
        "/register",        new Limit(5,   60_000),
        "/forgot-password", new Limit(5,   60_000),
        "/api/bookings",    new Limit(30,  60_000)
    );

    // key = "IP:path" -> timestamps of requests within the window
    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        if ("POST".equalsIgnoreCase(request.getMethod())) {
            String path  = request.getRequestURI();
            Limit  limit = ENDPOINT_LIMITS.get(path);
            if (limit != null && !tryConsume(clientIp(request) + ":" + path, limit)) {
                response.setStatus(429);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\":\"Too many requests. Please try again later.\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private boolean tryConsume(String key, Limit limit) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = buckets.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        // Evict old entries outside the window
        timestamps.removeIf(ts -> now - ts > limit.windowMillis());
        if (timestamps.size() >= limit.maxRequests()) {
            return false;
        }
        timestamps.addLast(now);
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
