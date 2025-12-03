package com.slackwise.slackwise.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Info {

    // Fields
    private String lastUpdated;
    private String updatedBy;

    // Getters and Setters
    
    public String getLastUpdated() {
        return lastUpdated;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

}

    
