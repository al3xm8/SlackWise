package com.slackwise.slackwise.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpRequest.BodyPublishers;

import java.nio.charset.StandardCharsets;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.slackwise.slackwise.model.TimeEntry;
import com.slackwise.slackwise.model.Note;
import com.slackwise.slackwise.model.Ticket;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ConnectwiseService {

    // Configuration properties for ConnectWise API
    @Value("${company.id}")
    private String companyId;

    @Value("${public.key}")
    private String publicKey;

    @Value("${private.key}")
    private String privateKey;

    @Value("${client.id}")
    private String clientId;

    private DateTimeFormatter PAYLOAD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));


    // Base URL for ConnectWise API
    private String baseUrl = "https://na.myconnectwise.net/v4_6_release/apis/3.0";

    // Create a single HttpClient instance
    HttpClient client = HttpClient.newHttpClient();
    
    // Cache of current open tickets
    public List<Ticket> tickets;

    @Autowired
    private com.slackwise.slackwise.service.AmazonService amazonService;

    // Authorization token for ConnectWise API (Base64 encoded "publicKey:privateKey")
    private String buildAuthHeader() {
        String rawAuth = companyId + "+" + publicKey + ":" + privateKey;
        String encodedAuth = Base64.getEncoder().encodeToString(rawAuth.getBytes());
        return "Basic " + encodedAuth;
    }

    /**
     * Adds a Slack reply as a note to the specified ConnectWise ticket.
     * 
     * @param companyId2
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public List<Ticket> fetchOpenTicketsByCompanyId(String companyId2) throws IOException, InterruptedException {

        // Prepare the request to be sent to ConnectWise API
        String conditions = URLEncoder.encode("company/id=" + companyId2 + " AND closedFlag=false", StandardCharsets.UTF_8);
        String fields = URLEncoder.encode("id,summary,board,status,contact,contactPhoneNumber,contactEmailAddress,type,subType,closedFlag,resources,actualHours,_info", StandardCharsets.UTF_8);
        String orderBy = URLEncoder.encode("id desc", StandardCharsets.UTF_8);    

        String endpoint = "/service/tickets";

        // Send the HTTP GET request to fetch ALL open tickets from companyId2
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint + "?conditions=" + conditions + "&fields=" + fields + "&orderBy=" + orderBy))
            .header("Authorization", buildAuthHeader())
            .header("clientId", clientId)
            .header("Content-Type", "application/json")
            .GET() 
            .build();
        
        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();
        ObjectMapper mapper = new ObjectMapper();

        // Parse the API response JSON into a List<Ticket>
        tickets = mapper.readValue(
            jsonResponse,
            new TypeReference<List<Ticket>>() {}
        );

        // For each ticket, populate its time entries and notes
        for (Ticket ticket : tickets) {
            ticket.setTimeEntries(fetchTimeEntriesByTicketId(String.valueOf(ticket.getId())));
            ticket.setNotes(fetchNotesByTicketId(String.valueOf(ticket.getId())));
            ticket.setDiscussion(ticket.getDiscussion());
        }

        return tickets;
    }

    /**
     * Fetches time entries associated with a specific ticket ID from ConnectWise.
     * 
     * @param ticketId
     * @return time entries for the ticket
     * @throws IOException
     * @throws InterruptedException
     */
    public List<TimeEntry> fetchTimeEntriesByTicketId(String ticketId) throws IOException, InterruptedException {

        // Prepare the request to be sent to ConnectWise API
        String conditions = URLEncoder.encode("chargeToId=" + ticketId, StandardCharsets.UTF_8);
        String fields = URLEncoder.encode("id,company,chargeToId,chargeToType,member,timeStart,timeEnd,actualHours,notes,addToDetailDescriptionFlag,addToInternalAnalysisFlag,addToResolutionFlag,emailCcFlag,emailCc,dateEntered,ticket,ticketBoard,ticketStatus,_info", StandardCharsets.UTF_8);

        System.out.println("Fetching ticket time entries for ticket " + ticketId);

        String endpoint = "/time/entries";
        // Send the HTTP GET request to fetch time entries for the ticket
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint + "?conditions=" + conditions + "&fields=" + fields))
            .header("Authorization", buildAuthHeader())
            .header("clientId", clientId)
            .header("Content-Type", "application/json")
            .GET()
            .build();
        
        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();
        ObjectMapper mapper = new ObjectMapper();

        // Parse the API response JSON into a List<TimeEntry>
        List<TimeEntry> timeEntries = mapper.readValue(
            jsonResponse,
            new TypeReference<List<TimeEntry>>() {}
        );        

        return timeEntries;
    }

    /**
     * Fetches notes associated with a specific ticket ID from ConnectWise.
     * 
     * @param ticketId
     * @return notes for the ticket
     * @throws IOException
     * @throws InterruptedException
     */
    public List<Note> fetchNotesByTicketId(String ticketId) throws IOException, InterruptedException {

        // Prepare the request to be sent to ConnectWise API
        String endpoint = "/service/tickets/" + ticketId + "/notes";

        System.out.println("Fetching notes for ticket " + ticketId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header("Authorization", buildAuthHeader())
            .header("clientId", clientId)
            .header("Content-Type", "application/json")
            .GET()
            .build();

        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();
        ObjectMapper mapper = new ObjectMapper();

        // Parse the API response JSON into a List<Note>
        List<Note> notes = mapper.readValue(
            jsonResponse,
            new TypeReference<List<Note>>() {}
        );

        return notes;
    }

    /**
     * Fetches a ticket by its ID.
     * 
     * @param companyId2
     * @param ticketId
     * @return the ticket with populated time entries, notes, etc.
     * @throws IOException
     * @throws InterruptedException
     */
    public Ticket fetchTicketById(String companyId2, String ticketId) throws IOException, InterruptedException {

        // Prepare the request to be sent to ConnectWise API
        String endpoint = "/service/tickets/" + ticketId;

        System.out.println("Fetching ticket ID " + ticketId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header("Authorization", buildAuthHeader())
            .header("clientId", clientId)
            .header("Content-Type", "application/json")
            .GET()
            .build();

        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();
        ObjectMapper mapper = new ObjectMapper();

        // Parse the API response JSON into a Ticket object
        Ticket ticket = mapper.readValue(
            jsonResponse,
            new TypeReference<Ticket>() {}
        );

        // Populate the ticket's time entries, notes, and discussion
        ticket.setTimeEntries(fetchTimeEntriesByTicketId(ticketId));
        ticket.setNotes(fetchNotesByTicketId(ticketId));
        ticket.setDiscussion(ticket.getDiscussion());

        //System.out.println("Ticket "+ ticketId + " discussion:\n" + ticket.printDiscussion());
        System.out.println("Ticket fetched.");

        return ticket;
    }

     /**
     * Get the contact name for a ticket.
     * 
     * @param ticket
     * @return contact name of the ticket
     * @throws IOException
     * @throws InterruptedException
     */
    public String getContactNameByTicketId(String ticketId) throws IOException, InterruptedException {
        
        Ticket ticket = fetchTicketById(companyId, ticketId);

        return ticket.getContact().getName();
    }
    
    /**
     * contact name of the note
     * 
     * @param note
     * @param ticketId 
     * @return @Note contact name
     * @throws IOException
     * @throws InterruptedException
     */
    public String getContactNameByTicketNoteId(String ticketId, int noteId) throws IOException, InterruptedException {

        Note note = fetchNoteByNoteTicketId(ticketId, String.valueOf(noteId));

        // This is the case when the note was converted from a time entry
        if (note.getContact() == null) {
            return note.getMember().getName();
        }

        return note.getContact().getName();
    }

    /**
     * Fetches a specific note by its note ID and associated ticket ID.
     * 
     * @param ticketId
     * @param noteId
     * @return the note with the specified note ID
     * @throws IOException
     * @throws InterruptedException
     */
    public Note fetchNoteByNoteTicketId(String ticketId, String noteId) throws IOException, InterruptedException {
        
        String endpoint = "/service/tickets/" + ticketId + "/notes/" + noteId;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header("Authorization", buildAuthHeader())
            .header("clientId", clientId)
            .header("Content-Type", "application/json")
            .GET()
            .build();   

        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();
        ObjectMapper mapper = new ObjectMapper();

        System.out.println(jsonResponse);
        // Parse the API response JSON into a List<Note>
        Note note = mapper.readValue(
            jsonResponse,
            new TypeReference<Note>() {}
        );

        return note;

    }

    /**
     * Adds a Slack reply as a note or time entry to the specified ConnectWise ticket.
     * 
     * @param ticketId
     * @param text
     * @param event
     * @throws InterruptedException 
     * @throws IOException 
     */
    public void addSlackReplyToTicket(String ticketId, String text, Map<String,Object> event) throws IOException, InterruptedException {

        //https://regex101.com/r/VXBKry/2
        Pattern commandPattern = Pattern.compile("\\$([\\w]+)=?([\\d.]+)?\"?(?:<mailto:)?([a-z@.\\d]+.com)?\\|?(?:[a-z@.\\d]+.com)?>?\"?");
        Matcher matcher = commandPattern.matcher(text);

        if (matcher.find()) {

            System.out.println("Reply being added as a time entry: " + text + " to ticket " + ticketId + " WITH commands");

            TimeEntry timeEntry = new TimeEntry();

            timeEntry.setTicketId(Integer.parseInt(ticketId));
            timeEntry.setDetailDescriptionFlag(true);
            timeEntry.setInternalAnalysisFlag(false);
            timeEntry.setResolutionFlag(false);
            timeEntry.setTimeStart(getCurrentTimeForPayload());
            timeEntry.setTimeEnd(null);
            timeEntry.setInfo(null);
            timeEntry.setActualHours(String.valueOf(0.15));
            timeEntry.setEmailCcFlag(false);
            timeEntry.setEmailContactFlag(true);
            timeEntry.setEmailResourceFlag(true);

            matcher.reset();
            parseCommands(timeEntry, matcher);

            String description = commandPattern.matcher(text).replaceAll("").trim();
            timeEntry.setNotes(description);

            String slackTs = (String) event.getOrDefault("ts", java.time.Instant.now().toString());
            String jsonResponse = addTimeEntryToTicket(companyId, ticketId, timeEntry);
            ObjectMapper mapper = new ObjectMapper();

            TimeEntry created = mapper.readValue(jsonResponse, TimeEntry.class);

            try {
                if (created != null) {
                    // Save ConnectWise ticket id and the Slack thread ts in DynamoDB so updateTicketThread won't repost it
                    amazonService.addNoteToTicket(ticketId, String.valueOf(created.getTimeEntryId()), slackTs);
                    System.out.println("Added ConnectWise Ticket ID " + created.getTimeEntryId() + " for ticket " + ticketId + " linked to Slack ts " + slackTs);
                    System.out.println("#" + ticketId + " - " + "Ticket text:\n" + timeEntry.getNotes());
                }
            } catch (Exception e) {
                // If parsing fails, fallback to saving the Slack client_msg_id if available
                String timeEntryId = String.valueOf(event.getOrDefault("client_msg_id", slackTs));
                amazonService.addNoteToTicket(ticketId, timeEntryId, slackTs);
            }

        } else {

            System.out.println("Reply being added as a time entry: " + text + " to ticket " + ticketId + " without commands");

            TimeEntry timeEntry = new TimeEntry();

            timeEntry.setTicketId(Integer.parseInt(ticketId));
            timeEntry.setDetailDescriptionFlag(true);
            timeEntry.setInternalAnalysisFlag(false);
            timeEntry.setResolutionFlag(false);
            timeEntry.setTimeStart(getCurrentTimeForPayload());
            timeEntry.setTimeEnd(null);
            timeEntry.setInfo(null);
            timeEntry.setActualHours(String.valueOf(0.15));
            timeEntry.setEmailCcFlag(false);
            timeEntry.setEmailContactFlag(true);
            timeEntry.setEmailResourceFlag(true);


            String description = commandPattern.matcher(text).replaceAll("").trim();
            timeEntry.setNotes(description);

            String slackTs = (String) event.getOrDefault("ts", java.time.Instant.now().toString());
            String jsonResponse = addTimeEntryToTicket(companyId, ticketId, timeEntry);
            ObjectMapper mapper = new ObjectMapper();

            TimeEntry created = mapper.readValue(jsonResponse, TimeEntry.class);

            try {
                if (created != null) {
                    // Save ConnectWise ticket id and the Slack thread ts in DynamoDB so updateTicketThread won't repost it
                    amazonService.addNoteToTicket(ticketId, String.valueOf(created.getTimeEntryId()), slackTs);
                    System.out.println("Added ConnectWise Ticket ID " + created.getTimeEntryId() + " for ticket " + ticketId + " linked to Slack ts " + slackTs);
                    System.out.println("#" + ticketId + " - " + "Ticket text:\n" + timeEntry.getNotes());
                }
            } catch (Exception e) {
                // If parsing fails, fallback to saving the Slack client_msg_id if available
                String timeEntryId = String.valueOf(event.getOrDefault("client_msg_id", slackTs));
                amazonService.addNoteToTicket(ticketId, timeEntryId, slackTs);
            }

            /**
             * Notes have minimal value compared to time entries as they dont log hours worked
             * Therefore removing the ability to add notes from Slack replies
             * 
            System.out.println("Reply beind added as a note: " + text + " to ticket " + ticketId);

            Note note = new Note();
            note.setTicketId(Integer.parseInt(ticketId));
            note.setText(text);
            note.setDetailDescriptionFlag(true);
            note.setInternalAnalysisFlag(false);
            note.setResolutionFlag(false);
            note.setDateCreated((String) event.getOrDefault("ts", java.time.Instant.now().toString()));
            note.setTimeStart(null);
            note.setTimeEnd(null);
            note.setInfo(null);
            
            String slackTs = (String) event.getOrDefault("ts", java.time.Instant.now().toString());
            
            try {
                // Add the note to ConnectWise
                String jsonResponse = addNoteToTicket(companyId, ticketId, note);
                ObjectMapper mapper = new ObjectMapper();
                 try {
                    Note created = mapper.readValue(jsonResponse, Note.class);
                    if (created != null) {
                        // Save ConnectWise note id and the Slack thread ts in DynamoDB
                        amazonService.addNoteToTicket(ticketId, String.valueOf(created.getId()), slackTs);
                        System.out.println("Added ConnectWise note ID " + created.getId() + " for ticket " + ticketId + " linked to Slack ts " + slackTs);
                        System.out.println("#" + ticketId + " - " + "Note text:\n" + note.getText());
                    }
                } catch (Exception e) {
                    // If parsing fails, fallback to saving the Slack client_msg_id if available
                    String noteId = String.valueOf(event.getOrDefault("client_msg_id", slackTs));
                    amazonService.addNoteToTicket(ticketId, noteId, slackTs);
                }
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
            */
        }
    }

    private void parseCommands(TimeEntry timeEntry, Matcher matcher) {        
        while (matcher.find()) {
            String command = matcher.group(1);

            for (int i = 0; i < matcher.groupCount(); i++) {
                System.out.println("Group " + i + ": " + matcher.group(i));
            }

            // Process each command accordingly
            // Sets the actual hours for the time entry
            if (command.equals("actualHours") || command.equals("actualhours")) {
                timeEntry.setActualHours(matcher.group(2));
                System.out.println("Set actual hours to " + matcher.group(2));
            // Makes it an internal time entry
            } else if (command.equals("internal")) {
                timeEntry.setInternalAnalysisFlag(true);
                timeEntry.setEmailContactFlag(false);
                timeEntry.setDetailDescriptionFlag(false);
                System.out.println("Set internal analysis flag to true and email contact flag to false");
                
            } else if (command.equals("resolution")) {
                timeEntry.setResolutionFlag(true);
                System.out.println("Set resolution flag to true");

            } else if (command.equals("ninja")) {
                timeEntry.setEmailContactFlag(false);
                timeEntry.setEmailCcFlag(false);
                timeEntry.setEmailResourceFlag(false);
                System.out.println("Set all email flags to false");

            } else if (command.equals("emailCc") || command.equals("emailcc")) {
                timeEntry.setEmailCcFlag(true);
                System.out.println("Set email CC flag to true");

            } else if (command.equals("cc") || command.equals("Cc")) {
                timeEntry.setCc(matcher.group(3));
                timeEntry.setEmailCcFlag(true);
                System.out.println("Set CC to " + matcher.group(3) + " and email CC flag to true");

            } else {
                System.out.println("Unknown command: " + command);
            }
        }
    }

    /*
     * Get the current time in ISO 8601 format for payloads.
     */
    private String getCurrentTimeForPayload() {

        ZonedDateTime localTime = ZonedDateTime.now(ZoneId.of("America/New_York"));
        Instant utcInstant = localTime.toInstant();
        return PAYLOAD_FORMATTER.format(utcInstant);

    }

    /**
     * Add a time entry to a specific ticket.
     * 
     * @param companyId2
     * @param ticketId
     * @param timeEntry
     * @return the created time entry as JSON string
     * @throws IOException
     * @throws InterruptedException
     */
    private String addTimeEntryToTicket(String companyId2, String ticketId, TimeEntry timeEntry) throws IOException, InterruptedException {
        java.util.Map<String, Object> payload = new java.util.HashMap<>();

        Ticket ticket = fetchTicketById(companyId2, ticketId);

        payload.put("company", ticket.getCompany());
        payload.put("companyType", "Client");
        payload.put("chargeToId", ticketId);
        payload.put("chargeToType", "ServiceTicket");
        payload.put("member", timeEntry.getMember());
        payload.put("billableOption", "Billable");
        payload.put("actualHours", Double.valueOf(timeEntry.getActualHours()));
        payload.put("timeStart", getCurrentTimeForPayload());
        payload.put("notes", timeEntry.getNotes());
        payload.put("addToDetailDescriptionFlag", timeEntry.isDetailDescriptionFlag());
        payload.put("addToInternalAnalysisFlag", timeEntry.isInternalAnalysisFlag());
        payload.put("addToResolutionFlag", timeEntry.isResolutionFlag());
        payload.put("emailResourceFlag", timeEntry.isEmailResourceFlag());
        payload.put("emailContactFlag", timeEntry.isEmailContactFlag());
        payload.put("emailCcFlag", timeEntry.isEmailCcFlag());
        payload.put("emailCc", timeEntry.getCc());
        // ensure ConnectWise processes notifications for this entry
        payload.put("invoiceReady", 1);

        // Prepare to send the request to ConnectWise API
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(payload);

        String endpoint = "/time/entries";

        // Send the HTTP POST request to create the time entry
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header("Authorization", buildAuthHeader())
            .header("clientId", clientId)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build();
        
        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();

        return jsonResponse;
    }

    /**
     * Adds a note to a specific ticket.
     * 
     * @param companyId2
     * @param ticketId
     * @param note
     * @return the created note as JSON string
     * @throws IOException
     * @throws InterruptedException
     */
    public String addNoteToTicket(String companyId2, String ticketId, Note note) throws IOException, InterruptedException {

        // Construct the payload for the new note
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("text", note.getText());
        payload.put("detailDescriptionFlag", note.isDetailDescriptionFlag());
        payload.put("internalAnalysisFlag", note.isInternalAnalysisFlag());
        payload.put("resolutionFlag", note.isResolutionFlag());
        payload.put("issueFlag", false);
        payload.put("contact", note.getContact());
        payload.put("customerUpdatedFlag", false);
        payload.put("processNotifications", true);
        payload.put("createdBy", note.getContact() != null ? note.getContact().getName() : null);
        payload.put("internalFlag", false);
        payload.put("externalFlag", true);
        payload.put("sentimentScore", 1);

        // Prepare to send the request to ConnectWise API
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(payload);

        String endpoint = "/service/tickets/" + ticketId + "/notes";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + endpoint))
            .header("Authorization", buildAuthHeader())
            .header("clientId", clientId)
            .header("Content-Type", "application/json")
            .POST(BodyPublishers.ofString(json))
            .build();

        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();

        // Parse the API response JSON into a Note object
        //Note createdNote = mapper.readValue(jsonResponse, Note.class);

        //Ticket ticket = fetchTicketById(companyId2, ticketId);

        return jsonResponse;
    }

    

}