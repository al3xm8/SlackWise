package com.slackwise.slackwise.model;

public class TenantConfigResponse {
    private String tenantId;
    private String slackTeamId;
    private String defaultChannelId;
    private String connectwiseSite;
    private String displayName;
    private Integer autoAssignmentDelayMinutes;
    private String assignmentExclusionKeywords;
    private String trackedCompanyIds;
    private String themeMode;
    private boolean slackConnected;
    private boolean connectwiseConfigured;

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getSlackTeamId() {
        return slackTeamId;
    }

    public void setSlackTeamId(String slackTeamId) {
        this.slackTeamId = slackTeamId;
    }

    public String getDefaultChannelId() {
        return defaultChannelId;
    }

    public void setDefaultChannelId(String defaultChannelId) {
        this.defaultChannelId = defaultChannelId;
    }

    public String getConnectwiseSite() {
        return connectwiseSite;
    }

    public void setConnectwiseSite(String connectwiseSite) {
        this.connectwiseSite = connectwiseSite;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getAutoAssignmentDelayMinutes() {
        return autoAssignmentDelayMinutes;
    }

    public void setAutoAssignmentDelayMinutes(Integer autoAssignmentDelayMinutes) {
        this.autoAssignmentDelayMinutes = autoAssignmentDelayMinutes;
    }

    public String getAssignmentExclusionKeywords() {
        return assignmentExclusionKeywords;
    }

    public void setAssignmentExclusionKeywords(String assignmentExclusionKeywords) {
        this.assignmentExclusionKeywords = assignmentExclusionKeywords;
    }

    public String getTrackedCompanyIds() {
        return trackedCompanyIds;
    }

    public void setTrackedCompanyIds(String trackedCompanyIds) {
        this.trackedCompanyIds = trackedCompanyIds;
    }

    public String getThemeMode() {
        return themeMode;
    }

    public void setThemeMode(String themeMode) {
        this.themeMode = themeMode;
    }

    public boolean isSlackConnected() {
        return slackConnected;
    }

    public void setSlackConnected(boolean slackConnected) {
        this.slackConnected = slackConnected;
    }

    public boolean isConnectwiseConfigured() {
        return connectwiseConfigured;
    }

    public void setConnectwiseConfigured(boolean connectwiseConfigured) {
        this.connectwiseConfigured = connectwiseConfigured;
    }
}
