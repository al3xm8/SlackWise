package com.slackwise.slackwise.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Note {
    private static final Logger log = LoggerFactory.getLogger(Note.class);

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
        StringBuilder sb = new StringBuilder();
        sb.append("Note ID: ").append(id).append('\n')
          .append("Ticket ID: ").append(ticketId).append('\n')
          .append("Text: ").append(text).append('\n')
          .append("Detail Description Flag: ").append(detailDescriptionFlag).append('\n')
          .append("Internal Analysis Flag: ").append(internalAnalysisFlag).append('\n')
          .append("Resolution Flag: ").append(resolutionFlag).append('\n');
        if (contact != null) {
            sb.append("Contact ID: ").append(contact.getID()).append('\n')
              .append("Contact Name: ").append(contact.getName()).append('\n');
        } else {
            sb.append("Member ID: ").append(member.getId()).append('\n')
              .append("Member Name: ").append(member.getName()).append('\n');
        }
        sb.append("Date Created: ").append(dateCreated).append('\n')
          .append("Time Start: ").append(timeStart).append('\n')
          .append("Time End: ").append(timeEnd);
        log.info("{}", sb.toString());
    }   

}
