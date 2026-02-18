package com.slackwise.slackwise.model;

public class RoutingRule {
    private String ruleId;
    private String tenantId;
    private int priority;
    private boolean enabled = true;
    private String matchContact;
    private String matchSubject;
    private String matchSubjectRegex;
    private String targetChannelId;

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
}
