package com.slackwise.slackwise.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slackwise.slackwise.model.RoutingRule;
import com.slackwise.slackwise.model.TenantConfig;
import com.slackwise.slackwise.model.TenantConfigResponse;
import com.slackwise.slackwise.model.TenantSecrets;
import com.slackwise.slackwise.model.TenantSecretsUpdateRequest;
import com.slackwise.slackwise.security.TenantAccessService;
import com.slackwise.slackwise.service.AmazonService;
import com.slackwise.slackwise.service.TenantSecretsService;

@RestController
@RequestMapping("/api/tenants")
public class TenantAdminController {

    @Autowired
    private AmazonService amazonService;

    @Autowired
    private TenantAccessService tenantAccessService;

    @Autowired
    private TenantSecretsService tenantSecretsService;

    @GetMapping("/default")
    public ResponseEntity<java.util.Map<String, String>> getDefaultTenant() {
        String tenantId = tenantAccessService.requiredTenantId();
        return ResponseEntity.ok(java.util.Map.of("tenantId", tenantId));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantConfigResponse> getTenantConfig(@PathVariable String tenantId) {
        validateTenantAccess(tenantId);
        TenantConfig config = amazonService.getTenantConfig(tenantId);
        TenantSecrets secrets = tenantSecretsService.getSecrets(tenantId);

        if (config == null && !secrets.hasAnySecrets()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toResponse(tenantId, config, secrets));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<TenantConfigResponse> putTenantConfig(@PathVariable String tenantId, @RequestBody TenantConfig config) {
        validateTenantAccess(tenantId);
        config.setTenantId(tenantId);
        config.setSlackBotToken(null);
        config.setSlackRefreshToken(null);
        config.setSlackTokenExpiresAt(null);
        config.setConnectwiseClientId(null);
        config.setConnectwisePublicKey(null);
        config.setConnectwisePrivateKey(null);
        amazonService.putTenantConfig(tenantId, config);
        return ResponseEntity.ok(toResponse(tenantId, amazonService.getTenantConfig(tenantId), tenantSecretsService.getSecrets(tenantId)));
    }

    @PutMapping("/{tenantId}/secrets")
    public ResponseEntity<Void> putTenantSecrets(
        @PathVariable String tenantId,
        @RequestBody TenantSecretsUpdateRequest request
    ) {
        validateTenantAccess(tenantId);
        if (request == null || (!request.hasSlackSecretFields() && !request.hasConnectwiseSecretFields())) {
            return ResponseEntity.badRequest().build();
        }

        if (request.hasSlackSecretFields()) {
            tenantSecretsService.upsertSlackSecrets(
                tenantId,
                request.getSlackBotToken(),
                request.getSlackRefreshToken(),
                request.getSlackTokenExpiresAt()
            );
        }
        if (request.hasConnectwiseSecretFields()) {
            tenantSecretsService.upsertConnectwiseSecrets(
                tenantId,
                request.getConnectwiseClientId(),
                request.getConnectwisePublicKey(),
                request.getConnectwisePrivateKey()
            );
        }

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{tenantId}/rules")
    public ResponseEntity<List<RoutingRule>> listRoutingRules(@PathVariable String tenantId) {
        validateTenantAccess(tenantId);
        return ResponseEntity.ok(amazonService.getRoutingRules(tenantId));
    }

    @PostMapping("/{tenantId}/rules")
    public ResponseEntity<RoutingRule> createRoutingRule(@PathVariable String tenantId, @RequestBody RoutingRule rule) {
        validateTenantAccess(tenantId);
        RoutingRule created = amazonService.putRoutingRule(tenantId, rule);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{tenantId}/rules/{ruleId}")
    public ResponseEntity<RoutingRule> updateRoutingRule(
        @PathVariable String tenantId,
        @PathVariable String ruleId,
        @RequestBody RoutingRule rule
    ) {
        validateTenantAccess(tenantId);
        rule.setRuleId(ruleId);
        RoutingRule updated = amazonService.putRoutingRule(tenantId, rule);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{tenantId}/rules/{ruleId}")
    public ResponseEntity<Void> deleteRoutingRule(
        @PathVariable String tenantId,
        @PathVariable String ruleId,
        @RequestParam("priority") int priority
    ) {
        validateTenantAccess(tenantId);
        amazonService.deleteRoutingRule(tenantId, priority, ruleId);
        return ResponseEntity.noContent().build();
    }

    private void validateTenantAccess(String tenantId) {
        tenantAccessService.validateTenantAccess(tenantId);
    }

    private TenantConfigResponse toResponse(String tenantId, TenantConfig config, TenantSecrets secrets) {
        TenantConfigResponse response = new TenantConfigResponse();
        response.setTenantId(tenantId);
        if (config != null) {
            response.setSlackTeamId(config.getSlackTeamId());
            response.setDefaultChannelId(config.getDefaultChannelId());
            response.setConnectwiseSite(config.getConnectwiseSite());
            response.setDisplayName(config.getDisplayName());
            response.setAutoAssignmentDelayMinutes(config.getAutoAssignmentDelayMinutes());
            response.setAssignmentExclusionKeywords(config.getAssignmentExclusionKeywords());
            response.setTrackedCompanyIds(config.getTrackedCompanyIds());
            response.setThemeMode(config.getThemeMode());
        }

        boolean slackConnected = (secrets != null && secrets.hasSlackAccess())
            || (config != null && isPresent(config.getSlackBotToken()));
        boolean connectwiseConfigured = isPresent(response.getConnectwiseSite()) && (
            (secrets != null && secrets.hasConnectwiseCredentials())
                || (config != null
                    && isPresent(config.getConnectwiseClientId())
                    && isPresent(config.getConnectwisePublicKey())
                    && isPresent(config.getConnectwisePrivateKey()))
        );

        response.setSlackConnected(slackConnected);
        response.setConnectwiseConfigured(connectwiseConfigured);
        return response;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
