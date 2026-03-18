package com.slackwise.slackwise.model;

public class TenantSecretsUpdateRequest {
    private String slackBotToken;
    private String slackRefreshToken;
    private Long slackTokenExpiresAt;
    private String connectwiseClientId;
    private String connectwisePublicKey;
    private String connectwisePrivateKey;

    public String getSlackBotToken() {
        return slackBotToken;
    }

    public void setSlackBotToken(String slackBotToken) {
        this.slackBotToken = slackBotToken;
    }

    public String getSlackRefreshToken() {
        return slackRefreshToken;
    }

    public void setSlackRefreshToken(String slackRefreshToken) {
        this.slackRefreshToken = slackRefreshToken;
    }

    public Long getSlackTokenExpiresAt() {
        return slackTokenExpiresAt;
    }

    public void setSlackTokenExpiresAt(Long slackTokenExpiresAt) {
        this.slackTokenExpiresAt = slackTokenExpiresAt;
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

    public boolean hasSlackSecretFields() {
        return isPresent(slackBotToken)
            || isPresent(slackRefreshToken)
            || slackTokenExpiresAt != null;
    }

    public boolean hasConnectwiseSecretFields() {
        return isPresent(connectwiseClientId)
            || isPresent(connectwisePublicKey)
            || isPresent(connectwisePrivateKey);
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
