package com.slackwise.slackwise.model;

public class Tenant {
    private String tenantId;

    public Tenant(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }
}