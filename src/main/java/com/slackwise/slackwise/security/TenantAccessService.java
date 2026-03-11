package com.slackwise.slackwise.security;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TenantAccessService {

    @Value("${app.auth.enabled:false}")
    private boolean authEnabled;

    @Value("${app.auth.tenant-claims:tenant_id,tenantId,company_id,org_id}")
    private String tenantClaimNamesCsv;

    @Value("${company.id:}")
    private String fallbackTenantId;

    @Value("${company.idnumber:}")
    private String fallbackCompanyIdNumber;

    public String requiredTenantId() {
        if (!authEnabled) {
            return requiredFallbackTenantId();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        Jwt jwt = extractJwt(authentication);
        if (jwt == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT authentication required");
        }

        for (String claimName : tenantClaimNames()) {
            Object claimValue = jwt.getClaims().get(claimName);
            if (claimValue instanceof String claimString && !claimString.isBlank()) {
                return normalizeTenantId(claimString);
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant claim missing from token");
    }

    public void validateTenantAccess(String requestedTenantId) {
        String normalizedRequested = normalizeTenantId(requestedTenantId);
        String callerTenant = normalizeTenantId(requiredTenantId());

        if (!callerTenant.equalsIgnoreCase(normalizedRequested)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tenant access denied");
        }
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

    private String requiredFallbackTenantId() {
        if (fallbackTenantId != null && !fallbackTenantId.isBlank()) {
            return normalizeTenantId(fallbackTenantId);
        }

        if (fallbackCompanyIdNumber != null && !fallbackCompanyIdNumber.isBlank()) {
            return normalizeTenantId(fallbackCompanyIdNumber);
        }

        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "No fallback tenant is configured. Set COMPANY_ID or COMPANY_ID_NUMBER."
        );
    }

    private String normalizeTenantId(String tenantId) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant ID is required");
        }

        String normalized = tenantId.trim();
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant ID is required");
        }

        return normalized;
    }
}
