package com.slackwise.slackwise.security;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class RequestTenantResolver {

    @Value("${app.auth.tenant-claims:tenant_id,tenantId,company_id,org_id}")
    private String tenantClaimNamesCsv;

    public String resolveTenantScope(HttpServletRequest request) {
        String fromHeader = normalize(request.getHeader("X-Tenant-Id"));
        if (fromHeader != null) {
            return fromHeader;
        }

        String fromQuery = normalize(request.getParameter("tenantId"));
        if (fromQuery != null) {
            return fromQuery;
        }

        String fromCompanyQuery = normalize(request.getParameter("companyId"));
        if (fromCompanyQuery != null) {
            return fromCompanyQuery;
        }

        String fromCompanyQueryAlt = normalize(request.getParameter("CompanyId"));
        if (fromCompanyQueryAlt != null) {
            return fromCompanyQueryAlt;
        }

        String fromSecurityContext = resolveFromSecurityContext();
        if (fromSecurityContext != null) {
            return fromSecurityContext;
        }

        return "GLOBAL";
    }

    private String resolveFromSecurityContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            return null;
        }

        for (String claimName : tenantClaimNames()) {
            Object value = jwt.getClaims().get(claimName);
            if (value instanceof String valueString) {
                String normalized = normalize(valueString);
                if (normalized != null) {
                    return normalized;
                }
            }
        }

        return null;
    }

    private Jwt extractJwt(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            return jwt;
        }

        Object credentials = authentication.getCredentials();
        if (credentials instanceof Jwt jwt) {
            return jwt;
        }

        return null;
    }

    private List<String> tenantClaimNames() {
        return Arrays.stream(tenantClaimNamesCsv.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.toList());
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.trim();
        if (normalized.isBlank()) {
            return null;
        }

        return normalized;
    }
}
