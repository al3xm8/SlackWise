package com.slackwise.slackwise.service;

import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.slackwise.slackwise.model.RoutingRule;
import com.slackwise.slackwise.model.Ticket;

@Service
public class RoutingService {

    @Autowired
    private AmazonService amazonService;

    public String resolveChannel(String tenantId, Ticket ticket, String defaultChannelId) {
        List<RoutingRule> rules = amazonService.getRoutingRules(tenantId);
        for (RoutingRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            if (rule.getTargetChannelId() == null || rule.getTargetChannelId().isBlank()) {
                continue;
            }
            if (matches(rule, ticket)) {
                return rule.getTargetChannelId();
            }
        }
        return defaultChannelId;
    }

    private static boolean matches(RoutingRule rule, Ticket ticket) {
        String contactName = "";
        if (ticket != null && ticket.getContact() != null && ticket.getContact().getName() != null) {
            contactName = ticket.getContact().getName();
        }
        String subject = ticket != null && ticket.getSummary() != null ? ticket.getSummary() : "";

        boolean hasCriteria = false;

        if (rule.getMatchContact() != null && !rule.getMatchContact().isBlank()) {
            hasCriteria = true;
            if (!containsIgnoreCase(contactName, rule.getMatchContact())) {
                return false;
            }
        }

        if (rule.getMatchSubject() != null && !rule.getMatchSubject().isBlank()) {
            hasCriteria = true;
            if (!containsIgnoreCase(subject, rule.getMatchSubject())) {
                return false;
            }
        }

        if (rule.getMatchSubjectRegex() != null && !rule.getMatchSubjectRegex().isBlank()) {
            hasCriteria = true;
            try {
                Pattern pattern = Pattern.compile(rule.getMatchSubjectRegex(), Pattern.CASE_INSENSITIVE);
                if (!pattern.matcher(subject).find()) {
                    return false;
                }
            } catch (PatternSyntaxException ex) {
                return false;
            }
        }

        return hasCriteria;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
