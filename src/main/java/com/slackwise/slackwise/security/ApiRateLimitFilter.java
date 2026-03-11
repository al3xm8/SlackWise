package com.slackwise.slackwise.security;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(ApiRateLimitFilter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, RequestCounter> counters = new ConcurrentHashMap<>();
    private final RequestTenantResolver requestTenantResolver;

    @Value("${security.rate-limit.enabled:true}")
    private boolean rateLimitEnabled;

    @Value("${security.rate-limit.window-seconds:60}")
    private long windowSeconds;

    @Value("${security.rate-limit.webhook-max-requests:120}")
    private int webhookMaxRequests;

    @Value("${security.rate-limit.auth-max-requests:30}")
    private int authMaxRequests;

    @Value("${security.rate-limit.per-tenant.enabled:true}")
    private boolean perTenantRateLimitEnabled;

    private volatile long lastCleanupEpochSeconds = 0L;

    public ApiRateLimitFilter(RequestTenantResolver requestTenantResolver) {
        this.requestTenantResolver = requestTenantResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        if (!rateLimitEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String routeGroup = SecurityRouteClassifier.classify(request.getRequestURI());
        if (routeGroup == null) {
            filterChain.doFilter(request, response);
            return;
        }

        int maxRequests = "webhook".equals(routeGroup) ? webhookMaxRequests : authMaxRequests;
        if (maxRequests <= 0 || windowSeconds <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        long now = Instant.now().getEpochSecond();
        cleanupStaleCounters(now);

        String clientIp = resolveClientIp(request);
        String tenantScope = perTenantRateLimitEnabled ? requestTenantResolver.resolveTenantScope(request) : "GLOBAL";
        String key = routeGroup + ":" + tenantScope + ":" + clientIp;

        RequestCounter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || now - existing.windowStartedEpochSeconds >= windowSeconds) {
                return new RequestCounter(now, 1);
            }

            existing.requestCount.incrementAndGet();
            return existing;
        });

        int currentCount = counter.requestCount.get();
        int remaining = Math.max(0, maxRequests - currentCount);
        long resetEpoch = counter.windowStartedEpochSeconds + windowSeconds;

        response.setHeader("X-RateLimit-Limit", String.valueOf(maxRequests));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetEpoch));
        response.setHeader("X-RateLimit-Tenant-Scope", tenantScope);

        if (currentCount > maxRequests) {
            long retryAfter = Math.max(1, resetEpoch - now);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);

            objectMapper.writeValue(response.getWriter(), Map.of(
                "error", "Rate limit exceeded",
                "routeGroup", routeGroup,
                "tenantScope", tenantScope,
                "retryAfterSeconds", retryAfter
            ));

            log.warn(
                "Rate limit exceeded for routeGroup={} tenantScope={} clientIp={} currentCount={} maxRequests={} windowSeconds={}",
                routeGroup,
                tenantScope,
                clientIp,
                currentCount,
                maxRequests,
                windowSeconds
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String[] parts = forwardedFor.split(",");
            if (parts.length > 0 && parts[0] != null && !parts[0].isBlank()) {
                return parts[0].trim();
            }
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    private void cleanupStaleCounters(long nowEpochSeconds) {
        if (nowEpochSeconds - lastCleanupEpochSeconds < 30) {
            return;
        }

        lastCleanupEpochSeconds = nowEpochSeconds;
        counters.entrySet().removeIf(entry -> nowEpochSeconds - entry.getValue().windowStartedEpochSeconds >= windowSeconds * 2);
    }

    private static class RequestCounter {
        private final long windowStartedEpochSeconds;
        private final AtomicInteger requestCount;

        private RequestCounter(long windowStartedEpochSeconds, int initialCount) {
            this.windowStartedEpochSeconds = windowStartedEpochSeconds;
            this.requestCount = new AtomicInteger(initialCount);
        }
    }
}
