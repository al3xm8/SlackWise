package com.slackwise.slackwise.security;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AuditLoggingFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(AuditLoggingFilter.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${security.audit.enabled:true}")
    private boolean auditEnabled;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        if (!auditEnabled) {
            filterChain.doFilter(request, response);
            return;
        }

        String routeGroup = SecurityRouteClassifier.classify(request.getRequestURI());
        if (routeGroup == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestId = resolveRequestId(request);
        response.setHeader("X-Request-Id", requestId);

        long startedAtNanos = System.nanoTime();
        Throwable thrown = null;

        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            thrown = ex;
            if (ex instanceof ServletException servletException) {
                throw servletException;
            }
            if (ex instanceof IOException ioException) {
                throw ioException;
            }
            if (ex instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ServletException(ex);
        } finally {
            long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000;
            int statusCode = response.getStatus();
            if (thrown != null && statusCode < 400) {
                statusCode = 500;
            }

            Map<String, Object> auditEvent = new LinkedHashMap<>();
            auditEvent.put("event", "api_audit");
            auditEvent.put("timestamp", Instant.now().toString());
            auditEvent.put("requestId", requestId);
            auditEvent.put("routeGroup", routeGroup);
            auditEvent.put("method", request.getMethod());
            auditEvent.put("path", request.getRequestURI());
            auditEvent.put("query", request.getQueryString());
            auditEvent.put("status", statusCode);
            auditEvent.put("durationMs", durationMs);
            auditEvent.put("clientIp", resolveClientIp(request));
            auditEvent.put("userAgent", request.getHeader("User-Agent"));
            auditEvent.put("tenantId", resolveTenantId(request));
            auditEvent.put("principal", resolvePrincipal());
            auditEvent.put("rateLimited", statusCode == 429);

            if (thrown != null) {
                auditEvent.put("exception", thrown.getClass().getSimpleName());
                auditEvent.put("exceptionMessage", thrown.getMessage());
            }

            log.info(objectMapper.writeValueAsString(auditEvent));
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String incoming = request.getHeader("X-Request-Id");
        if (incoming != null && !incoming.isBlank()) {
            return incoming.trim();
        }
        return UUID.randomUUID().toString();
    }

    private String resolveTenantId(HttpServletRequest request) {
        String fromHeader = request.getHeader("X-Tenant-Id");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader.trim();
        }

        String fromQuery = request.getParameter("tenantId");
        if (fromQuery != null && !fromQuery.isBlank()) {
            return fromQuery.trim();
        }

        return "";
    }

    private String resolvePrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return "anonymous";
        }

        if (auth.getName() != null && !auth.getName().isBlank()) {
            return auth.getName();
        }

        return "authenticated";
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
}

