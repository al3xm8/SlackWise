package com.slackwise.slackwise.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Contact {

    // Fields
    
    private int id;
    private String name;

    // Constructors

    public Contact(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Contact() {
        id = 0;
        name = "N/A";
    }

    // Getters and Setters

    public void setId(int id) {
        this.id = id;
    }

    public int getID() {
        return this.id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}

