package com.slackwise.slackwise.service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slackwise.slackwise.model.Note;
import com.slackwise.slackwise.model.TenantConfig;
import com.slackwise.slackwise.model.TenantSecrets;
import com.slackwise.slackwise.model.Ticket;
import com.slackwise.slackwise.model.TimeEntry;
import com.slackwise.slackwise.util.TextFormatTranslator;

@Service
public class ConnectwiseService {
    private static final Logger log = LoggerFactory.getLogger(ConnectwiseService.class);

    // Configuration properties for ConnectWise API
    @Value("${company.id}")
    private String companyId;

    @Value("${public.key}")
    private String publicKey;

    @Value("${private.key}")
    private String privateKey;

    @Value("${client.id}")
    private String clientId;
    
    @Value("${user.id}")
    private int userId;
    
    @Value("${user.identifier}")
    private String userIdentifier;
    
    // Slack configuration properties
    @Value("${slack.bot.token}")
    private String slackBotToken;

    @Value("${slack.channel.id}")
    private String slackChannelId;
    
    private final Slack slack = Slack.getInstance();
    
    private DateTimeFormatter PAYLOAD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneId.of("UTC"));

    // Base URL for ConnectWise API
    private String baseUrl = "https://na.myconnectwise.net/v4_6_release/apis/3.0";

    // Create a single HttpClient instance
    HttpClient client = HttpClient.newHttpClient();
    
    // Cache of current open tickets
    public List<Ticket> tickets;

    @Autowired
    private com.slackwise.slackwise.service.AmazonService amazonService;

    @Autowired
    private SlackTokenManager slackTokenManager;

    @Autowired
    private TenantSecretsService tenantSecretsService;

    private static class ConnectwiseCredentials {
        private final String authCompanyId;
        private final String clientId;
        private final String publicKey;
        private final String privateKey;
        private final String baseUrl;

        private ConnectwiseCredentials(String authCompanyId, String clientId, String publicKey, String privateKey, String baseUrl) {
            this.authCompanyId = authCompanyId;
            this.clientId = clientId;
            this.publicKey = publicKey;
            this.privateKey = privateKey;
            this.baseUrl = baseUrl;
        }
    }


    
    // Authorization token for ConnectWise API (Base64 encoded "publicKey:privateKey")
    public String buildAuthHeader(String tenantId) throws IOException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);
        String rawAuth = credentials.authCompanyId + "+" + credentials.publicKey + ":" + credentials.privateKey;
        String encodedAuth = Base64.getEncoder().encodeToString(rawAuth.getBytes());
        return "Basic " + encodedAuth;
    }

    private ConnectwiseCredentials resolveConnectwiseCredentials(String tenantId) throws IOException {
        TenantConfig config = (tenantId != null && !tenantId.isBlank()) ? amazonService.getTenantConfig(tenantId) : null;
        TenantSecrets secrets = (tenantId != null && !tenantId.isBlank()) ? tenantSecretsService.getSecrets(tenantId) : new TenantSecrets();

        String resolvedAuthCompanyId = firstPresent(tenantId, companyId);
        String resolvedClientId = firstPresent(secrets.getConnectwiseClientId(), config != null ? config.getConnectwiseClientId() : null, clientId);
        String resolvedPublicKey = firstPresent(secrets.getConnectwisePublicKey(), config != null ? config.getConnectwisePublicKey() : null, publicKey);
        String resolvedPrivateKey = firstPresent(secrets.getConnectwisePrivateKey(), config != null ? config.getConnectwisePrivateKey() : null, privateKey);
        String resolvedBaseUrl = buildBaseUrl(config != null ? config.getConnectwiseSite() : null);

        if (!isPresent(resolvedAuthCompanyId) || !isPresent(resolvedClientId) || !isPresent(resolvedPublicKey) || !isPresent(resolvedPrivateKey)) {
            throw new IOException("ConnectWise credentials are incomplete for tenantId=" + tenantId);
        }

        return new ConnectwiseCredentials(
            resolvedAuthCompanyId.trim(),
            resolvedClientId.trim(),
            resolvedPublicKey.trim(),
            resolvedPrivateKey.trim(),
            resolvedBaseUrl
        );
    }

    private String buildBaseUrl(String configuredSite) {
        if (!isPresent(configuredSite)) {
            return baseUrl;
        }

        String trimmed = configuredSite.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed.endsWith("/v4_6_release/apis/3.0")
                ? trimmed
                : trimmed.replaceAll("/+$", "") + "/v4_6_release/apis/3.0";
        }

        return "https://" + trimmed + "/v4_6_release/apis/3.0";
    }

    private String firstPresent(String... values) {
        for (String value : values) {
            if (isPresent(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 
     * Fetches all open tickets for a given company ID from ConnectWise. 
     * This method constructs a query to retrieve tickets that are not closed and belong to the specified company.
     * 
     * @param companyId2
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public List<Ticket> fetchOpenTicketsByCompanyId(String companyId2) throws IOException, InterruptedException {
        return fetchOpenTicketsByCompanyId(companyId, companyId2);
    }

    public List<Ticket> fetchOpenTicketsByCompanyId(String tenantId, String companyId2) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);

        // Prepare the request to be sent to ConnectWise API
        String conditions = URLEncoder.encode(buildCompanyFilterCondition(companyId2) + " AND closedFlag=false", StandardCharsets.UTF_8);
        String fields = URLEncoder.encode("id,summary,board,status,contact,contactPhoneNumber,contactEmailAddress,type,subType,closedFlag,resources,actualHours,_info", StandardCharsets.UTF_8);
        String orderBy = URLEncoder.encode("id desc", StandardCharsets.UTF_8);    

        String endpoint = "/service/tickets";

        // Send the HTTP GET request to fetch ALL open tickets from companyId2
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint + "?conditions=" + conditions + "&fields=" + fields + "&orderBy=" + orderBy))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
            .header("Content-Type", "application/json")
            .GET() 
            .build();
        
        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        String jsonResponse = response.body();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("ConnectWise ticket fetch failed with HTTP " + statusCode + ". Body: " + safeBodySnippet(jsonResponse));
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(jsonResponse);

        // ConnectWise returns an array for success; some environments return an object wrapper.
        if (root.isArray()) {
            tickets = mapper.convertValue(root, new TypeReference<List<Ticket>>() {});
        } else if (root.isObject() && root.has("items") && root.get("items").isArray()) {
            tickets = mapper.convertValue(root.get("items"), new TypeReference<List<Ticket>>() {});
        } else {
            throw new IOException("Unexpected ConnectWise ticket response shape: " + root.getNodeType()
                + ". Body: " + safeBodySnippet(jsonResponse));
        }

        // For each ticket, populate its time entries and notes
        for (Ticket ticket : tickets) {
            
            Ticket fullTicket = fetchTicketById(tenantId, companyId2, String.valueOf(ticket.getId()));
            ticket.setCompany(fullTicket.getCompany());
            ticket.setSeverity(fullTicket.getSeverity());
            ticket.setPriority(fullTicket.getPriority());
            ticket.setTimeEntries(fetchTimeEntriesByTicketId(tenantId, String.valueOf(ticket.getId())));
            ticket.setNotes(fetchNotesByTicketId(tenantId, String.valueOf(ticket.getId())));
            ticket.setDiscussion(ticket.getDiscussion());
        }
        return tickets;
    }
    /**
     * Fetch open tickets across all tracked company IDs and merge into one list.
     * Duplicate ticket IDs are removed.
     *
     * @param companyIds tracked company IDs/identifiers
     * @return merged open ticket list
     * @throws IOException
     * @throws InterruptedException
     */
    public List<Ticket> fetchOpenTicketsByCompanyIds(List<String> companyIds) throws IOException, InterruptedException {
        return fetchOpenTicketsByCompanyIds(companyId, companyIds);
    }

    public List<Ticket> fetchOpenTicketsByCompanyIds(String tenantId, List<String> companyIds) throws IOException, InterruptedException {
        if (companyIds == null || companyIds.isEmpty()) {
            return java.util.List.of();
        }

        java.util.Map<Integer, Ticket> mergedTickets = new java.util.LinkedHashMap<>();
        IOException firstIoException = null;

        for (String companyIdValue : companyIds) {
            if (companyIdValue == null || companyIdValue.isBlank()) {
                continue;
            }

            String normalizedCompanyId = companyIdValue.trim();
            try {
                List<Ticket> companyTickets = fetchOpenTicketsByCompanyId(tenantId, normalizedCompanyId);
                for (Ticket ticket : companyTickets) {
                    mergedTickets.putIfAbsent(ticket.getId(), ticket);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw ex;
            } catch (IOException ex) {
                if (firstIoException == null) {
                    firstIoException = ex;
                }
                log.error("Failed to fetch open tickets for companyId={}", normalizedCompanyId, ex);
            }
        }

        if (mergedTickets.isEmpty() && firstIoException != null) {
            throw firstIoException;
        }

        return new java.util.ArrayList<>(mergedTickets.values());
    }

    private String buildCompanyFilterCondition(String companyValue) {
        if (companyValue == null || companyValue.isBlank()) {
            throw new IllegalArgumentException("company value is null/blank");
        }

        String trimmed = companyValue.trim();
        if (trimmed.matches("\\d+")) {
            return "company/id=" + trimmed;
        }

        String escaped = trimmed.replace("'", "''");
        return "company/identifier='" + escaped + "'";
    }

    private static String safeBodySnippet(String body) {
        if (body == null) {
            return "<empty>";
        }

        String compact = body.replaceAll("\\s+", " ").trim();
        int maxLength = 300;
        if (compact.length() <= maxLength) {
            return compact;
        }
        return compact.substring(0, maxLength) + "...";
    }

    private static String normalizeMessageLineEndings(String value) {
        if (value == null) return null;
        // Normalize mixed line endings first, then emit CRLF for ConnectWise/email rendering.
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.replace("\n", "\r\n");
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
        return fetchTimeEntriesByTicketId(companyId, ticketId);
    }

    public List<TimeEntry> fetchTimeEntriesByTicketId(String tenantId, String ticketId) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);

        // Prepare the request to be sent to ConnectWise API
        String conditions = URLEncoder.encode("chargeToId=" + ticketId, StandardCharsets.UTF_8);
        String fields = URLEncoder.encode("id,company,chargeToId,chargeToType,member,timeStart,timeEnd,actualHours,notes,addToDetailDescriptionFlag,addToInternalAnalysisFlag,addToResolutionFlag,emailCcFlag,emailCc,dateEntered,ticket,ticketBoard,ticketStatus,_info", StandardCharsets.UTF_8);

        String endpoint = "/time/entries";
        // Send the HTTP GET request to fetch time entries for the ticket
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint + "?conditions=" + conditions + "&fields=" + fields))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
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
        return fetchNotesByTicketId(companyId, ticketId);
    }

    public List<Note> fetchNotesByTicketId(String tenantId, String ticketId) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);

        // Prepare the request to be sent to ConnectWise API
        String endpoint = "/service/tickets/" + ticketId + "/notes";

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
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
        return fetchTicketById(companyId, companyId2, ticketId);
    }

    public Ticket fetchTicketById(String tenantId, String companyId2, String ticketId) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);

        // Prepare the request to be sent to ConnectWise API
        String endpoint = "/service/tickets/" + ticketId;

        log.debug("Fetching ticketId={}", ticketId);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
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
        ticket.setTimeEntries(fetchTimeEntriesByTicketId(tenantId, ticketId));
        ticket.setNotes(fetchNotesByTicketId(tenantId, ticketId));
        ticket.setDiscussion(ticket.getDiscussion());

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
        return getContactNameByTicketId(companyId, ticketId);
    }

    public String getContactNameByTicketId(String tenantId, String ticketId) throws IOException, InterruptedException {
        Ticket ticket = fetchTicketById(tenantId, tenantId, ticketId);

        return ticket.getContact().getName();
    }
    
    /**
     * Get the contact name for a Note.
     * 
     * @param note
     * @param ticketId 
     * @return contact name of the note
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
        return fetchNoteByNoteTicketId(companyId, ticketId, noteId);
    }

    public Note fetchNoteByNoteTicketId(String tenantId, String ticketId, String noteId) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);
        
        String endpoint = "/service/tickets/" + ticketId + "/notes/" + noteId;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
            .header("Content-Type", "application/json")
            .GET()
            .build();   

        // Receive and process the response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        String jsonResponse = response.body();
        ObjectMapper mapper = new ObjectMapper();

        log.debug("Fetched note payload for ticketId={} noteId={}", ticketId, noteId);
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
     * @throws SlackApiException 
     */
    public void addSlackReplyToTicket(String tenantId, String ticketId, String text, Map<String,Object> event) throws IOException, InterruptedException, SlackApiException {

        //https://regex101.com/r/6uC4Tj/2
        Pattern commandPattern = Pattern.compile("\\$([\\w\\d]+)=?([\\d.]+)?(([\\w\\d@.]+);\\n" + //
                        "?([\\w\\d@.]+);\\n" + //
                        "?([\\w\\d@.]+);?\\n" + //
                        "?|([\\w\\d@.]+);\\n" + //
                        "?([\\w\\d@.]+);?\\n" + //
                        "?|([\\w\\d@.]+);?\\n" + //
                        "?)?");

        Matcher matcher = commandPattern.matcher(text);

        if (matcher.find()) {

            log.info("Adding Slack reply as time entry to ticketId={} with commands", ticketId);

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
            try {
                parseCommands(tenantId, timeEntry, matcher);
            } catch (IOException | InterruptedException e) {
                log.error("Failed parsing commands for ticketId={}", ticketId, e);
                return;
            }
            String description = commandPattern.matcher(text).replaceAll("").trim();
            description = TextFormatTranslator.slackToConnectwise(description);
            timeEntry.setNotes(description);

            String slackTs = (String) event.getOrDefault("ts", java.time.Instant.now().toString());
            String jsonResponse = addTimeEntryToTicket(tenantId, tenantId, ticketId, timeEntry);
            ObjectMapper mapper = new ObjectMapper();

            TimeEntry created = mapper.readValue(jsonResponse, TimeEntry.class);

            try {
                if (created != null) {
                    // Save ConnectWise ticket id and the Slack thread ts in DynamoDB so updateTicketThread won't repost it
                    amazonService.addNoteToTicket(tenantId, ticketId, String.valueOf(created.getTimeEntryId()), slackTs);
                    log.info("Added ConnectWise timeEntryId={} for ticketId={} linked to Slack ts={}", created.getTimeEntryId(), ticketId, slackTs);
                    log.debug("ticketId={} time entry text={}", ticketId, timeEntry.getNotes());
                }
            } catch (Exception e) {
                // If parsing fails, fallback to saving the Slack client_msg_id if available
                String timeEntryId = String.valueOf(event.getOrDefault("client_msg_id", slackTs));
                amazonService.addNoteToTicket(tenantId, ticketId, timeEntryId, slackTs);
            }

        } else {

            log.info("Adding Slack reply as time entry to ticketId={} without commands", ticketId);

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
            description = TextFormatTranslator.slackToConnectwise(description);
            timeEntry.setNotes(description);

            String slackTs = (String) event.getOrDefault("ts", java.time.Instant.now().toString());
            String jsonResponse = addTimeEntryToTicket(tenantId, tenantId, ticketId, timeEntry);
            ObjectMapper mapper = new ObjectMapper();

            TimeEntry created = mapper.readValue(jsonResponse, TimeEntry.class);

            try {
                if (created != null) {
                    // Save ConnectWise ticket id and the Slack thread ts in DynamoDB so updateTicketThread won't repost it
                    amazonService.addNoteToTicket(tenantId, ticketId, String.valueOf(created.getTimeEntryId()), slackTs);
                    log.info("Added ConnectWise timeEntryId={} for ticketId={} linked to Slack ts={}", created.getTimeEntryId(), ticketId, slackTs);
                    log.debug("ticketId={} time entry text={}", ticketId, timeEntry.getNotes());
                }
            } catch (Exception e) {
                // If parsing fails, fallback to saving the Slack client_msg_id if available
                String timeEntryId = String.valueOf(event.getOrDefault("client_msg_id", slackTs));
                amazonService.addNoteToTicket(tenantId, ticketId, timeEntryId, slackTs);
            }
        }
    }

    private void parseCommands(String tenantId, TimeEntry timeEntry, Matcher matcher) throws IOException, InterruptedException, SlackApiException {        
        while (matcher.find()) {
            String command = matcher.group(1);

            // Process each command accordingly
            // Sets the actual hours for the time entry
            if (command.equals("actualHours") || command.equals("actualhours")) {
                timeEntry.setActualHours(matcher.group(2));
                log.debug("Set actualHours={} for ticketId={}", matcher.group(2), timeEntry.getTicketId());
            // Makes it an internal time entry
            } else if (command.equals("internal")) {
                timeEntry.setInternalAnalysisFlag(true);
                timeEntry.setEmailContactFlag(false);
                timeEntry.setDetailDescriptionFlag(false);
                log.debug("Set internal analysis flags for ticketId={}", timeEntry.getTicketId());
                
            } else if (command.equals("resolution")) {
                timeEntry.setResolutionFlag(true);
                log.debug("Set resolution flag for ticketId={}", timeEntry.getTicketId());

            } else if (command.equals("ninja")) {
                timeEntry.setEmailContactFlag(false);
                timeEntry.setEmailCcFlag(false);
                timeEntry.setEmailResourceFlag(false);
                log.debug("Set all email flags false for ticketId={}", timeEntry.getTicketId());

            } else if (command.equals("emailCc") || command.equals("emailcc")) {
                timeEntry.setEmailCcFlag(true);
                log.debug("Enabled email CC for ticketId={}", timeEntry.getTicketId());
            
                // Sets the CC email addresses for the time entry (only up to 3 emails supported)
            } else if (command.equals("cc") || command.equals("Cc")) {

                if (matcher.group(9) != null) {
                    timeEntry.setCc(matcher.group(9));
                } else if (matcher.group(7) != null) {
                    timeEntry.setCc(matcher.group(7) + ";" + matcher.group(8));
                } else if (matcher.group(4) != null) {
                    timeEntry.setCc(matcher.group(4) + ";" + matcher.group(5) + ";" + matcher.group(6));
                }
                
                timeEntry.setEmailCcFlag(true);
                log.debug("Set CC recipients for ticketId={}", timeEntry.getTicketId());
            
            } else if (command.equals("am")) {
                Ticket ticket = fetchTicketById(tenantId, tenantId, String.valueOf(timeEntry.getTicketId()));

                this.assignTicketTo(tenantId, userId, userIdentifier, timeEntry.getTicketId());
            } else {
                
                String validToken = slackTokenManager.getValidBotToken(tenantId);
                if (validToken != null && !validToken.isBlank()) {
                    ChatPostMessageResponse response = slack.methods(validToken).chatPostMessage(req -> req
                        .channel(slackChannelId)
                        .text("ERR0R: The command $" + command + " is not recognized. Supported commands are: $actualHours=<hours>, $internal, $resolution, $ninja, $emailCc, $cc=<email1>;<email2>;<email3>, $am")
                        .mrkdwn(true)
                    );
                }
                
                throw new IOException("The command $" + command + " is not recognized.");
            }
        }
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
    public String addTimeEntryToTicket(String companyId2, String ticketId, TimeEntry timeEntry) throws IOException, InterruptedException {
        return addTimeEntryToTicket(companyId, companyId2, ticketId, timeEntry);
    }

    public String addTimeEntryToTicket(String tenantId, String companyId2, String ticketId, TimeEntry timeEntry) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);
        java.util.Map<String, Object> payload = new java.util.HashMap<>();

        Ticket ticket = fetchTicketById(tenantId, companyId2, ticketId);

        payload.put("company", ticket.getCompany());
        payload.put("companyType", "Client");
        payload.put("chargeToId", ticketId);
        payload.put("chargeToType", "ServiceTicket");
        payload.put("member", timeEntry.getMember());
        payload.put("billableOption", "Billable");
        payload.put("actualHours", Double.valueOf(timeEntry.getActualHours()));
        payload.put("timeStart", getCurrentTimeForPayload());
        payload.put("notes", normalizeMessageLineEndings(timeEntry.getNotes()));
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
            .uri(URI.create(credentials.baseUrl + endpoint))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
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
        return addNoteToTicket(companyId, companyId2, ticketId, note);
    }

    public String addNoteToTicket(String tenantId, String companyId2, String ticketId, Note note) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);

        // Construct the payload for the new note
        java.util.Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("ticketId", ticketId);
        payload.put("text", normalizeMessageLineEndings(note.getText()));
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
            .uri(URI.create(credentials.baseUrl + endpoint))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
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

    /*
     * Get the current time in ISO 8601 format for payloads.
     */
    public String getCurrentTimeForPayload() {

        ZonedDateTime localTime = ZonedDateTime.now(ZoneId.of("America/New_York"));
        Instant utcInstant = localTime.toInstant();
        
        return PAYLOAD_FORMATTER.format(utcInstant);

    }

    /**
     * Assigns a ticket to a specific user in ConnectWise by updating the ticket's owner field. This method constructs a JSON Patch request to replace the owner information of the specified ticket with the provided user ID and identifier. It then sends a PATCH request to the ConnectWise API to update the ticket accordingly. This is typically used when a user wants to claim a ticket or reassign it to someone else directly from Slack commands or interactions. The method also logs the assignment action for auditing purposes.
     * @param userId
     * @param userIdentifier
     * @param ticketId
     * @throws IOException
     * @throws InterruptedException
     */
    public void assignTicketTo(int userId, String userIdentifier, int ticketId) throws IOException, InterruptedException {
        assignTicketTo(companyId, userId, userIdentifier, ticketId);
    }

    public void assignTicketTo(String tenantId, int userId, String userIdentifier, int ticketId) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);

        // Build JSON Patch operations
        List<Map<String, Object>> ops = new java.util.ArrayList<>();

        Map<String, Object> ownerVal = new java.util.HashMap<>();
        ownerVal.put("id", userId);
        ownerVal.put("identifier", userIdentifier);

        Map<String, Object> ownerOp = new java.util.HashMap<>();
        ownerOp.put("op", "replace");
        ownerOp.put("path", "owner"); // or "/owner"
        ownerOp.put("value", ownerVal);
        ops.add(ownerOp);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ops);

        String endpoint = "/service/tickets/" + ticketId;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
            .header("Content-Type", "application/json") // try application/json-patch+json if needed
            .method("PATCH", BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Assigned ticketId={} to user={}", ticketId, userIdentifier);
    }

    public void assignTicketToIdentifier(String userIdentifierToAssign, int ticketId) throws IOException, InterruptedException {
        assignTicketToIdentifier(companyId, userIdentifierToAssign, ticketId);
    }

    public void assignTicketToIdentifier(String tenantId, String userIdentifierToAssign, int ticketId) throws IOException, InterruptedException {
        if (userIdentifierToAssign == null || userIdentifierToAssign.isBlank()) {
            throw new IllegalArgumentException("userIdentifierToAssign is null/blank");
        }

        Integer memberId = findMemberIdByIdentifier(tenantId, userIdentifierToAssign.trim());
        if (memberId == null) {
            throw new IOException("Could not resolve ConnectWise member id for identifier: " + userIdentifierToAssign);
        }

        assignTicketTo(tenantId, memberId, userIdentifierToAssign.trim(), ticketId);
    }

    private Integer findMemberIdByIdentifier(String tenantId, String identifier) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);
        String escapedIdentifier = identifier.replace("'", "''");
        String conditions = URLEncoder.encode("identifier='" + escapedIdentifier + "'", StandardCharsets.UTF_8);
        String fields = URLEncoder.encode("id,identifier", StandardCharsets.UTF_8);

        String endpoint = "/system/members";
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint + "?conditions=" + conditions + "&fields=" + fields + "&pageSize=1"))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
            .header("Content-Type", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("ConnectWise member lookup failed with HTTP " + statusCode + ". Body: " + safeBodySnippet(response.body()));
        }

        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response.body());
        if (!root.isArray() || root.isEmpty()) {
            return null;
        }

        JsonNode member = root.get(0);
        if (!member.has("id") || !member.get("id").canConvertToInt()) {
            return null;
        }
        return member.get("id").asInt();
    }

    /**
     * Updates a ticket status by status name.
     *
     * @param companyId2 company identifier/id
     * @param ticketId ticket id
     * @param statusName ConnectWise status name
     * @throws IOException
     * @throws InterruptedException
     */
    public void updateTicketStatus(String companyId2, String ticketId, String statusName) throws IOException, InterruptedException {
        updateTicketStatus(companyId, companyId2, ticketId, statusName);
    }

    public void updateTicketStatus(String tenantId, String companyId2, String ticketId, String statusName) throws IOException, InterruptedException {
        ConnectwiseCredentials credentials = resolveConnectwiseCredentials(tenantId);
        if (statusName == null || statusName.isBlank()) {
            throw new IllegalArgumentException("statusName is null/blank");
        }

        List<Map<String, Object>> ops = new java.util.ArrayList<>();
        Map<String, Object> statusVal = new java.util.HashMap<>();
        statusVal.put("name", statusName.trim());

        Map<String, Object> statusOp = new java.util.HashMap<>();
        statusOp.put("op", "replace");
        statusOp.put("path", "status");
        statusOp.put("value", statusVal);
        ops.add(statusOp);

        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(ops);
        String endpoint = "/service/tickets/" + ticketId;

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(credentials.baseUrl + endpoint))
            .header("Authorization", buildAuthHeader(tenantId))
            .header("clientId", credentials.clientId)
            .header("Content-Type", "application/json")
            .method("PATCH", BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 300) {
            throw new IOException("ConnectWise ticket status update failed with HTTP " + statusCode + ". Body: " + safeBodySnippet(response.body()));
        }
    }
    

}

// https://regex101.com/r/eqaa9m/1

