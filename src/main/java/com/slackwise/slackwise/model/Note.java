package com.slackwise.slackwise.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Note {

    // Fields

    private int id;

    private int ticketId;

    private String text;

    private boolean detailDescriptionFlag;

    private boolean internalAnalysisFlag;

    private boolean resolutionFlag;

    private Contact contact;

    private Member member;

    private String dateCreated;

    private String timeStart;
    
    private String timeEnd;

    @JsonProperty("_info")
    private Info info;

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isDetailDescriptionFlag() {
        return detailDescriptionFlag;
    }

    public void setDetailDescriptionFlag(boolean detailDescriptionFlag) {
        this.detailDescriptionFlag = detailDescriptionFlag;
    }

    public boolean isInternalAnalysisFlag() {
        return internalAnalysisFlag;
    }

    public void setInternalAnalysisFlag(boolean internalAnalysisFlag) {
        this.internalAnalysisFlag = internalAnalysisFlag;
    }

    public boolean isResolutionFlag() {
        return resolutionFlag;
    }

    public void setResolutionFlag(boolean resolutionFlag) {
        this.resolutionFlag = resolutionFlag;
    }


    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public String getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(String dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getTimeStart() {
        return timeStart;
    }

    public void setTimeStart(String timeStart) {
        this.timeStart = timeStart;
    }

    public String getTimeEnd() {
        return timeEnd;
    }

    public void setTimeEnd(String timeEnd) {
        this.timeEnd = timeEnd;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public void setContactId(int contactId) {
        this.contact.setId(contactId);
    }

    public void printNote() {
        System.out.println("Note ID: " + id);
        System.out.println("Ticket ID: " + ticketId);
        System.out.println("Text: " + text);
        System.out.println("Detail Description Flag: " + detailDescriptionFlag);
        System.out.println("Internal Analysis Flag: " + internalAnalysisFlag);
        System.out.println("Resolution Flag: " + resolutionFlag);
        if (contact != null) {
            System.out.println("Contact ID: " + contact.getID());
            System.out.println("Contact Name: " + contact.getName());
        } else {
            System.out.println("Member ID: " + member.getId());
            System.out.println("Member Name: " + member.getName());
        }
        System.out.println("Date Created: " + dateCreated);
        System.out.println("Time Start: " + timeStart);
        System.out.println("Time End: " + timeEnd);
    }   

}