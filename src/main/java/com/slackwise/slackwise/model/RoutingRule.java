package com.slackwise.slackwise.model;

import java.util.List;

public class RoutingRule {
    private String ruleId;
    private String tenantId;
    private int priority;
    private boolean enabled = true;
    private String matchContact;
    private String matchSubject;
    private String matchSubjectRegex;
    private String targetChannelId;
    private String targetAssigneeIdentifier;
    private String primaryField;
    private String primaryOperator;
    private String primaryValue;
    private String secondaryField;
    private String secondaryOperator;
    private String secondaryValue;
    private String joinOperator;
    private List<RuleCondition> conditions;

    public static class RuleCondition {
        private String field;
        private String operator;
        private String value;

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getOperator() {
            return operator;
        }

        public void setOperator(String operator) {
            this.operator = operator;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    public String getRuleId() {
        return ruleId;
    }

    public void setRuleId(String ruleId) {
        this.ruleId = ruleId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMatchContact() {
        return matchContact;
    }

    public void setMatchContact(String matchContact) {
        this.matchContact = matchContact;
    }

    public String getMatchSubject() {
        return matchSubject;
    }

    public void setMatchSubject(String matchSubject) {
        this.matchSubject = matchSubject;
    }

    public String getMatchSubjectRegex() {
        return matchSubjectRegex;
    }

    public void setMatchSubjectRegex(String matchSubjectRegex) {
        this.matchSubjectRegex = matchSubjectRegex;
    }

    public String getTargetChannelId() {
        return targetChannelId;
    }

    public void setTargetChannelId(String targetChannelId) {
        this.targetChannelId = targetChannelId;
    }

    public String getTargetAssigneeIdentifier() {
        return targetAssigneeIdentifier;
    }

    public void setTargetAssigneeIdentifier(String targetAssigneeIdentifier) {
        this.targetAssigneeIdentifier = targetAssigneeIdentifier;
    }

    public String getPrimaryField() {
        return primaryField;
    }

    public void setPrimaryField(String primaryField) {
        this.primaryField = primaryField;
    }

    public String getPrimaryOperator() {
        return primaryOperator;
    }

    public void setPrimaryOperator(String primaryOperator) {
        this.primaryOperator = primaryOperator;
    }

    public String getPrimaryValue() {
        return primaryValue;
    }

    public void setPrimaryValue(String primaryValue) {
        this.primaryValue = primaryValue;
    }

    public String getSecondaryField() {
        return secondaryField;
    }

    public void setSecondaryField(String secondaryField) {
        this.secondaryField = secondaryField;
    }

    public String getSecondaryOperator() {
        return secondaryOperator;
    }

    public void setSecondaryOperator(String secondaryOperator) {
        this.secondaryOperator = secondaryOperator;
    }

    public String getSecondaryValue() {
        return secondaryValue;
    }

    public void setSecondaryValue(String secondaryValue) {
        this.secondaryValue = secondaryValue;
    }

    public String getJoinOperator() {
        return joinOperator;
    }

    public void setJoinOperator(String joinOperator) {
        this.joinOperator = joinOperator;
    }

    public List<RuleCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<RuleCondition> conditions) {
        this.conditions = conditions;
    }
}
