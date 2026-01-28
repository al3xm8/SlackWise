package com.slackwise.slackwise.model;

public class TenantConfig {
    private String tenantId;
    private String slackTeamId;
    private String slackBotToken;
    private String defaultChannelId;
    private String connectwiseSite;
    private String connectwiseClientId;
    private String connectwisePublicKey;
    private String connectwisePrivateKey;
    private String displayName;

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

    public String getSlackBotToken() {
        return slackBotToken;
    }

    public void setSlackBotToken(String slackBotToken) {
        this.slackBotToken = slackBotToken;
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

    public String getConnectwiseClientId() {
        return connectwiseClientId;
    }

    public void setConnectwiseClientId(String connectwiseClientId) {
        this.connectwiseClientId = connectwiseClientId;
    }

    public String getConnectwisePublicKey() {
        return connectwisePublicKey;
    }

    public void setConnectwisePublicKey(String connectwisePublicKey) {
        this.connectwisePublicKey = connectwisePublicKey;
    }

    public String getConnectwisePrivateKey() {
        return connectwisePrivateKey;
    }

    public void setConnectwisePrivateKey(String connectwisePrivateKey) {
        this.connectwisePrivateKey = connectwisePrivateKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
