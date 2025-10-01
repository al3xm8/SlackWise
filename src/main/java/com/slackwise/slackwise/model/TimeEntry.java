package com.slackwise.slackwise.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TimeEntry {

    @JsonProperty("id")
    private int timeEntryId;

    @JsonProperty("chargeToId")
    private int ticketId;

    private String chargeToType;
    
    private String notes;
    
    private String timeStart;
    
    private String timeEnd;

    private String actualHours;
        
    private String hoursDeduct;
    
    @JsonProperty("addToDetailDescriptionFlag")
    private boolean detailDescriptionFlag;
    
    @JsonProperty("addToInternalAnalysisFlag")
    private boolean internalAnalysisFlag;
    
    @JsonProperty("addToResolutionFlag")
    private boolean resolutionFlag;

    private boolean emailResourceFlag;

    private boolean emailContactFlag;
    
    private boolean emailCcFlag;
    
    @JsonProperty("member")
    private Member member;
    
    private String dateEntered;
    
    private String ticketBoard;

    private String ticketStatus;


    @JsonProperty("_info")
    private Info info;

    // Getters and Setters

    public int getTimeEntryId() {
    return timeEntryId;
    }

    public void setTimeEntryId(int timeEntryId) {
        this.timeEntryId = timeEntryId;
    }

    public int getTicketId() {
        return ticketId;
    }

    public void setTicketId(int ticketId) {
        this.ticketId = ticketId;
    }

    public String getChargeToType() {
        return chargeToType;
    }

    public void setChargeToType(String chargeToType) {
        this.chargeToType = chargeToType;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public String getActualHours() {
        return actualHours;
    }

    public void setActualHours(String actualHours) {
        this.actualHours = actualHours;
    }

    public String getHoursDeduct() {
        return hoursDeduct;
    }

    public void setHoursDeduct(String hoursDeduct) {
        this.hoursDeduct = hoursDeduct;
    }

    public boolean isEmailResourceFlag () {
        return emailResourceFlag;
    }

    public void setEmailResourceFlag(boolean emailResourceFlag) {
        this.emailResourceFlag = emailResourceFlag;
    }

    public boolean isEmailContactFlag () {
        return emailContactFlag;
    }

    public void setEmailContactFlag (boolean emailContactFlag) {
        this.emailContactFlag = emailContactFlag;
    }

    public boolean isEmailCcFlag () {
        return emailCcFlag;
    }

    public void setEmailCcFlag (boolean emailCcFlag) {
        this.emailCcFlag = emailCcFlag;
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

    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public String getDateEntered() {
        return dateEntered;
    }

    public void setDateEntered(String dateEntered) {
        this.dateEntered = dateEntered;
    }

    public String getTicketBoard() {
        return ticketBoard;
    }

    public void setTicketBoard(String ticketBoard) {
        this.ticketBoard = ticketBoard;
    }

    public String getTicketStatus() {
        return ticketStatus;
    }

    public void setTicketStatus(String ticketStatus) {
        this.ticketStatus = ticketStatus;
    }

    public Info getInfo() {
        return info;
    }

    public void setInfo(Info info) {
        this.info = info;
    }

    public int getMemberId() {
        return this.member.getId();
    }

    public String getMemberName() {        
        return this.member.getName();
    }


}