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
import com.slackwise.slackwise.service.AmazonService;

@RestController
@RequestMapping("/api/tenants")
public class TenantAdminController {

    @Autowired
    private AmazonService amazonService;

    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantConfig> getTenantConfig(@PathVariable String tenantId) {
        TenantConfig config = amazonService.getTenantConfig(tenantId);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(config);
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<TenantConfig> putTenantConfig(@PathVariable String tenantId, @RequestBody TenantConfig config) {
        config.setTenantId(tenantId);
        amazonService.putTenantConfig(tenantId, config);
        return ResponseEntity.ok(config);
    }

    @GetMapping("/{tenantId}/rules")
    public ResponseEntity<List<RoutingRule>> listRoutingRules(@PathVariable String tenantId) {
        return ResponseEntity.ok(amazonService.getRoutingRules(tenantId));
    }

    @PostMapping("/{tenantId}/rules")
    public ResponseEntity<RoutingRule> createRoutingRule(@PathVariable String tenantId, @RequestBody RoutingRule rule) {
        RoutingRule created = amazonService.putRoutingRule(tenantId, rule);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{tenantId}/rules/{ruleId}")
    public ResponseEntity<RoutingRule> updateRoutingRule(
        @PathVariable String tenantId,
        @PathVariable String ruleId,
        @RequestBody RoutingRule rule
    ) {
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
        amazonService.deleteRoutingRule(tenantId, priority, ruleId);
        return ResponseEntity.noContent().build();
    }
}
