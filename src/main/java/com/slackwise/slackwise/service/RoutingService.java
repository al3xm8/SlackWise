package com.slackwise.slackwise.service;

import java.util.List;
import java.util.stream.Collectors;
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
        RoutingRule matchedRule = resolveRule(tenantId, ticket);
        if (matchedRule != null
            && matchedRule.getTargetChannelId() != null
            && !matchedRule.getTargetChannelId().isBlank()) {
            return matchedRule.getTargetChannelId();
        }
        return defaultChannelId;
    }

    public RoutingRule resolveRule(String tenantId, Ticket ticket) {
        List<RoutingRule> rules = amazonService.getRoutingRules(tenantId);
        for (RoutingRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            if (!hasDestination(rule)) {
                continue;
            }
            if (matches(rule, ticket)) {
                return rule;
            }
        }
        return null;
    }

    private static boolean hasDestination(RoutingRule rule) {
        boolean hasChannel = rule.getTargetChannelId() != null && !rule.getTargetChannelId().isBlank();
        boolean hasAssignee = rule.getTargetAssigneeIdentifier() != null && !rule.getTargetAssigneeIdentifier().isBlank();
        return hasChannel || hasAssignee;
    }

    private static boolean matches(RoutingRule rule, Ticket ticket) {
        if (hasSentenceCondition(rule)) {
            return matchesSentenceRule(rule, ticket);
        }
        return matchesLegacyRule(rule, ticket);
    }

    private static boolean hasSentenceCondition(RoutingRule rule) {
        if (rule.getConditions() != null && !rule.getConditions().isEmpty()) {
            return rule.getConditions().stream().anyMatch(condition ->
                isPresent(condition.getField()) && isPresent(condition.getOperator()) && isPresent(condition.getValue())
            );
        }
        return isPresent(rule.getPrimaryField()) && isPresent(rule.getPrimaryOperator()) && isPresent(rule.getPrimaryValue());
    }

    private static boolean matchesSentenceRule(RoutingRule rule, Ticket ticket) {
        List<RoutingRule.RuleCondition> conditions = extractConditions(rule);
        if (conditions.isEmpty()) {
            return false;
        }

        String joinOperator = normalize(rule.getJoinOperator());
        if (conditions.size() == 1) {
            RoutingRule.RuleCondition onlyCondition = conditions.get(0);
            return evaluateCondition(onlyCondition.getField(), onlyCondition.getOperator(), onlyCondition.getValue(), ticket);
        }

        if ("OR".equals(joinOperator)) {
            return conditions.stream()
                .anyMatch(condition -> evaluateCondition(condition.getField(), condition.getOperator(), condition.getValue(), ticket));
        }

        return conditions.stream()
            .allMatch(condition -> evaluateCondition(condition.getField(), condition.getOperator(), condition.getValue(), ticket));
    }

    private static boolean evaluateCondition(String field, String operator, String expected, Ticket ticket) {
        String actual = valueByField(field, ticket);
        if (actual == null) {
            actual = "";
        }
        String normalizedOperator = normalize(operator);
        String expectedValue = expected == null ? "" : expected.trim();

        if ("EQUALS".equals(normalizedOperator)) {
            return actual.equalsIgnoreCase(expectedValue);
        }
        if ("NOT_EQUALS".equals(normalizedOperator)
            || "DOES_NOT_EQUAL".equals(normalizedOperator)
            || "DOES_NOT_EQUALS".equals(normalizedOperator)) {
            return !actual.equalsIgnoreCase(expectedValue);
        }
        if ("CONTAINS".equals(normalizedOperator)) {
            return containsIgnoreCase(actual, expectedValue);
        }
        return false;
    }

    private static String valueByField(String field, Ticket ticket) {
        String normalizedField = normalize(field);
        if ("CONTACT".equals(normalizedField)) {
            if (ticket != null && ticket.getContact() != null && ticket.getContact().getName() != null) {
                return ticket.getContact().getName();
            }
            return "";
        }
        if ("SUBJECT".equals(normalizedField)) {
            return ticket != null && ticket.getSummary() != null ? ticket.getSummary() : "";
        }
        if ("COMPANY_ID".equals(normalizedField)) {
            if (ticket != null && ticket.getCompany() != null && ticket.getCompany().getId() > 0) {
                return String.valueOf(ticket.getCompany().getId());
            }
            return "";
        }
        return "";
    }

    private static boolean matchesLegacyRule(RoutingRule rule, Ticket ticket) {
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

    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    private static List<RoutingRule.RuleCondition> extractConditions(RoutingRule rule) {
        if (rule.getConditions() != null && !rule.getConditions().isEmpty()) {
            return rule.getConditions().stream()
                .filter(condition -> condition != null
                    && isPresent(condition.getField())
                    && isPresent(condition.getOperator())
                    && isPresent(condition.getValue()))
                .collect(Collectors.toList());
        }

        java.util.ArrayList<RoutingRule.RuleCondition> conditions = new java.util.ArrayList<>();

        if (isPresent(rule.getPrimaryField()) && isPresent(rule.getPrimaryOperator()) && isPresent(rule.getPrimaryValue())) {
            RoutingRule.RuleCondition primary = new RoutingRule.RuleCondition();
            primary.setField(rule.getPrimaryField());
            primary.setOperator(rule.getPrimaryOperator());
            primary.setValue(rule.getPrimaryValue());
            conditions.add(primary);
        }

        if (isPresent(rule.getSecondaryField()) && isPresent(rule.getSecondaryOperator()) && isPresent(rule.getSecondaryValue())) {
            RoutingRule.RuleCondition secondary = new RoutingRule.RuleCondition();
            secondary.setField(rule.getSecondaryField());
            secondary.setOperator(rule.getSecondaryOperator());
            secondary.setValue(rule.getSecondaryValue());
            conditions.add(secondary);
        }

        return conditions;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack.toLowerCase().contains(needle.toLowerCase());
    }
}
