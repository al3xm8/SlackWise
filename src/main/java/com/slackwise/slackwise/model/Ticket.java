package com.slackwise.slackwise.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Ticket {

    // Fields
    private int id;
    private String summary;
    private Board board;
    private Status status;
    private Company company;
    private Contact contact;
    private Owner owner;
    private String contactPhoneNumber;
    private Priority priority;
    private String contactEmailAddress;
    private String severity;
    private String impact;
    private Team team;
    private Type type;
    private SubType subType;
    private boolean closedFlag;
    private double actualHours;
    private String resources;
    private List<TimeEntry> timeEntries = new ArrayList<>();
    private List<Note> notes = new ArrayList<>();
    private List<Note> discussion = new ArrayList<>();


    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Board {
        public int id;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Status {
        public int id;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Company {
        private int id;
        private String identifier;

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
        
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Type {
        public int id;
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class SubType {
        public int id;
        public String name;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Owner {
        public int id;
        public String identifier;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Priority {
        public int id;
        public String name;
        public int sort;
        private String level;
    }
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Team {
        public int id;
        public String name;
    }

    // Getters and Setters

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Board getBoard() {
        return board;
    }

    public void setBoard(Board board) {
        this.board = board;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }


    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public String getContactPhoneNumber() {
        return contactPhoneNumber;
    }

    public void setContactPhoneNumber(String contactPhoneNumber) {
        this.contactPhoneNumber = contactPhoneNumber;
    }

    public String getContactEmailAddress() {
        return contactEmailAddress;
    }

    public void setContactEmailAddress(String contactEmailAddress) {
        this.contactEmailAddress = contactEmailAddress;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public SubType getSubType() {
        return subType;
    }

    public void setSubType(SubType subType) {
        this.subType = subType;
    }

    public boolean isClosedFlag() {
        return closedFlag;
    }

    public void setClosedFlag(boolean closedFlag) {
        this.closedFlag = closedFlag;
    }

    public double getActualHours() {
        return actualHours;
    }

    public void setActualHours(double actualHours) {
        this.actualHours = actualHours;
    }

    public String getResources() {
        return resources;
    }

    public void setResources(String resources) {
        this.resources = resources;
    }


    public void setTimeEntries(List<TimeEntry> timeEntries) {
        this.timeEntries = timeEntries;
    }

    public List<TimeEntry> getTimeEntries() {
        return timeEntries;
    }

    public List<Note> getNotes() {
        return notes;
    }

    public void setNotes(List<Note> notes) {
        this.notes = notes;
    }
    
    public Owner getOwner() {
        return owner;
    }
    
    public void setOwner(int id, String identifier) {
        Owner owner = new Owner();
        owner.id = id;
        owner.identifier = identifier;
    }
    
    public void setPriority(Priority priority) {
        this.priority = priority;
    }
    
    public Priority getPriority() {
        return priority;
    }
    
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    public String getImpact() {
        return impact;
    }
    
    public void setImpact(String impact) {
        this.impact = impact;
    }
    
    public Team getTeam() {
        return team;
    }
    
    public void setTeam(Team team) {
        this.team = team;
    }

    public void setDiscussion(List<Note> discussion) {
        this.discussion = discussion;
    }

    public List<Note> getDiscussionList() {
        return discussion;
    }

    
    // Returns all notes from the ticket and time entries, sorted by date.
    public List<Note> getDiscussion() {
        List<Note> allNotes = new ArrayList<>();

        //System.out.println("Fetching discussion for ticket " + id);

        // Add existing notes
        if (notes != null) {
            allNotes.addAll(notes);
        }

        // Add notes from time entries as Note objects
        if (timeEntries != null) {
            for (TimeEntry te : timeEntries) {
                if (te.getNotes() != null && !te.getNotes().isEmpty()) {
                    Note n = new Note();
                    n.setId(te.getTimeEntryId());
                    n.setTicketId(te.getTicketId());
                    n.setText(te.getNotes());

                    Contact c = new Contact();
                    c.setId(te.getMemberId());
                    c.setName(te.getMemberName());
                    n.setContact(c);

                    n.setDetailDescriptionFlag(te.isDetailDescriptionFlag());
                    n.setInternalAnalysisFlag(te.isInternalAnalysisFlag());
                    n.setResolutionFlag(te.isResolutionFlag());

                    n.setDateCreated(te.getDateEntered());
                    n.setTimeStart(te.getTimeStart());
                    n.setTimeEnd(te.getTimeEnd());

                    if (te.getInfo() != null) {
                        Info info = new Info();
                        info.setLastUpdated(te.getInfo().getLastUpdated());
                        info.setUpdatedBy(te.getInfo().getUpdatedBy());
                        n.setInfo(info);
                    }

                    allNotes.add(n);
                }
            }
        }

        // Sort by dateCreated (for Note) or dateEntered (for TimeEntry-derived Note)
        return allNotes.stream()
            .sorted(Comparator.comparing(note -> parseDate(note.getDateCreated())))
            .collect(Collectors.toList());
    }

    // Helper method to parse date strings safely
    private static ZonedDateTime parseDate(String dateStr) {
        if (dateStr == null) return ZonedDateTime.parse("1970-01-01T00:00:00Z");
        return ZonedDateTime.parse(dateStr);
    }

    // Debugging methods to print notes and time entries

    public String printNotes() {
        StringBuilder sb = new StringBuilder();
        for (Note note : notes) {
            sb.append("Note ID: ").append(note.getId())
              .append(", Contact: ").append(note.getContact() != null ? note.getContact().getName() : note.getMember().getName())
              .append(", Date Created: ").append(note.getDateCreated())
              .append(", Text:\n").append(note.getText())
              .append("\n");
        }
        return sb.toString();
    }

    public String printTimeEntries() {
        StringBuilder sb = new StringBuilder();
        for (TimeEntry te : timeEntries) {
            sb.append("Time Entry ID: ").append(te.getTimeEntryId())
              .append(", Member: ").append(te.getMember() != null ? te.getMember().getName() : "N/A")
              .append(", Date Entered: ").append(te.getDateEntered())
              .append(", Notes:\n").append(te.getNotes())
              .append("\n");
        }
        return sb.toString();
    }

    public String printDiscussion() {
        StringBuilder sb = new StringBuilder();
        for (Note note : discussion) {
            sb.append("Note ID: ").append(note.getId())
              .append(", Contact: ").append(note.getContact() != null ? note.getContact().getName() : note.getMember().getName())
              .append(", Date Created: ").append(note.getDateCreated())
              .append(", Text:\n").append(note.getText())
              .append("\n");
        }
        return sb.toString();
    }

    public void printTicket(int ticketId) {

        System.out.println("Ticket ID: " + ticketId);
        System.out.println("Summary: " + summary);
        System.out.println("Status: " + (status != null ? status.name : "N/A"));
        System.out.println("Company: " + (company != null ? company.getIdentifier() : "N/A"));
        System.out.println("Contact: " + (contact != null ? contact.getName() : "N/A"));
        System.out.println("Contact Phone: " + contactPhoneNumber);
        System.out.println("Contact Email: " + contactEmailAddress);
        System.out.println("Type: " + (type != null ? type.name : "N/A"));
        System.out.println("SubType: " + (subType != null ? subType.name : "N/A"));
        System.out.println("Closed Flag: " + closedFlag);
        System.out.println("Actual Hours: " + actualHours);
        System.out.println("Resources: " + resources);
        System.out.println("Time Entries:\n" + printTimeEntries());
        System.out.println("Notes:\n" + printNotes());
        System.out.println("Discussion:\n" + printDiscussion());
    }
}
