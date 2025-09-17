package com.slackwise.slackwise.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Member {

    // Fields
    private int id;
    private String identifier;
    private String name;

    // Constructors
    public Member() {
        id = 0;
        identifier = "N/A";
        name = "N/A";
    }

    public Member(int id, String identifier, String name) {
        this.id = id;
        this.identifier = identifier;
        this.name = name;
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }

}