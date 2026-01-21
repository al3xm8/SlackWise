package com.slackwise.slackwise.controller;

import java.io.IOException;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slackwise.slackwise.model.Tenant;
import com.slackwise.slackwise.model.Ticket;
import com.slackwise.slackwise.model.TimeEntry;
import com.slackwise.slackwise.service.SlackService;
import com.slackwise.slackwise.service.ConnectwiseService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.methods.SlackApiException;
import jakarta.annotation.PostConstruct;



@RestController
@RequestMapping("/api/connectwise")
public class ConnectwiseController {

    @Autowired
    ConnectwiseService connectwiseService;

    @Autowired
    SlackService slackService;

    Tenant tenant;

    private ArrayList<String> companies = new ArrayList<String>();
    
    // Depreceated: now using database to track which tickets have been posted to Slack
    private Set<Integer> openTicketList = new HashSet<>();

    // add a simple lock object to serialize onNewEvent() calls
    private final Object eventLock = new Object();

    @Value("${client.id}")
    private String clientId;

    @Value("${user.id}")
    private int userId;
    
    @Value("${user.identifier}")
    private String userIdentifier;
    
    @Value("${lead.contact.name}")
    private String leadContactName;
    
    // Base URL for ConnectWise API
    private String baseUrl = "https://na.myconnectwise.net/v4_6_release/apis/3.0";

    /**
     * Initializes the ticket service by fetching all open tickets for a specific company.
     * This method is called after the bean is constructed.
     */
    @PostConstruct
    public void initTickets() {
        companies.add("19300"); // Test company ID, add more if you want to track multiple companies
        //companies.add("250");
    }

    
    /**
     * Fetches all tickets for a given company ID.
     *
     * @param companyId The ID of the company.
     * @return List of all Open tickets from company.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the request is interrupted.
     */
    @GetMapping("/tickets/{companyId}")
    public List<Ticket> getOpenTicketsByCompanyId(@PathVariable String companyId) throws IOException, InterruptedException {

        List<Ticket> tickets = connectwiseService.fetchOpenTicketsByCompanyId(companyId);
        System.out.println("Fetched " + tickets.size() + " tickets for company ID: " + companyId);

        for (Ticket t : tickets) {
            openTicketList.add(t.getId());
        }

        return tickets;
    }

    /**
     * Handles ConnectWise ticket events including ticket creation and updates.
     * 
     * @param recordId
     * @param payload
     * @return ResponseEntity with status message
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SlackApiException 
     */
    @PostMapping("/events")
    public ResponseEntity<String> onNewEvent(@RequestParam("recordId") String recordId,@RequestBody Map<String, Object> payload) throws IOException, InterruptedException, SlackApiException {

        synchronized (eventLock) {
            
            System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Received ConnectWise "+ payload.get("Action") + " event for recordId: " + recordId);

            ObjectMapper mapper = new ObjectMapper();

            /*
              
              Get tenantId from payload
             
              */

            if (!payload.containsKey("CompanyId")) {
                System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> No CompanyId found in payload");
                System.out.println("__________________________________________________________________"); // Separator for logs
                return ResponseEntity.badRequest().body("No CompanyId found in payload");
            }

            tenant = new Tenant(String.valueOf(payload.get("CompanyId"))); 
            slackService.tenant = tenant;
            System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Extracted tenant ID: " + tenant.getTenantId());

            /*
                Get ticket ID from payload
            */ 
            Integer ticketId = -1;

            if (payload.containsKey("ID")) {
                ticketId = Integer.valueOf(String.valueOf(payload.get("ID")));
                System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Extracted ticket ID: " + ticketId);
            } else  {
                System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> No ticket ID found in payload");
                System.out.println("__________________________________________________________________"); // Separator for logs
                return ResponseEntity.badRequest().body("No ticket ID found in payload");
            }

            /*
                Get entity and companyId from payload
            */
            String entityJson = "";
            String companyId = "";

            if (payload.containsKey("Entity")) {
                entityJson = String.valueOf(payload.get("Entity"));

                // Parse entity JSON string into a Map
                Map<String, Object> entity = null;
                if (entityJson != null && !"null".equals(entityJson)) {
                    entity = mapper.readValue(entityJson, new TypeReference<Map<String, Object>>() {});
                    System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Extracted entity from payload");
                }

                if (entity == null) {
                    System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Entity is null (something has been deleted). Skipping processing for ticket ID: " + recordId);
                    System.out.println("__________________________________________________________________"); // Separator for logs
                    return ResponseEntity.badRequest().body("No Entity found in payload");
                }

                // Extract companyId
                @SuppressWarnings("unchecked")
                Map<String, Object> company = (Map<String, Object>) entity.get("company");
                
                if (company != null && company.get("id") != null) {
                    companyId = String.valueOf(company.get("id"));
                    System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Extracted companyId: " + companyId);
                }

            } else {
                System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> No Entity found in payload. (This is likely not a ticket.)");
                System.out.println(payload);
                System.out.println("__________________________________________________________________"); // Separator for logs
                return ResponseEntity.badRequest().body("No Entity found in payload");
            }

            /*
                Process ticket from payload
            */
            
            // Only process if the companyId is in our list of companies to track
            if (companies.contains(companyId)) {
                Ticket ticket = connectwiseService.fetchTicketById(companyId, ticketId.toString());

                // If ticketFetch successful
                if (ticket != null) {

                    // Only process if action is "added" or "updated"
                    if (payload.get("Action").equals("added") || payload.get("Action").equals("updated")){

                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Posting new Slack message for ticket: " + ticketId + " - " + ticket.getSummary());
                        slackService.postNewTicket(ticketId.toString(), ticket.getSummary());

                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Updating Slack Thread for new ticket " + ticketId);
                        slackService.updateTicketThread(ticketId.toString(), ticket.getDiscussion(), ticket.getSummary());
                        
                        // Check if ticket is unassigned and assign to user if it is (with some exceptions for compliance/internal review tickets) after a delay to allow for any automatic assignment rules to run in ConnectWise first
                        final int finalTicketId = ticketId;
                        final String finalCompanyId = companyId;
                        if (payload.get("Action").equals("added")) {
                            new Thread(() -> {
                                try {
                                    
                                    Thread.sleep(120000); // Wait for 2 minutes before checking for assignment
                                    Ticket ticket2 = connectwiseService.fetchTicketById(finalCompanyId, String.valueOf(finalTicketId));
                                    System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Re-fetched ticket " + finalTicketId + " after delay to check for assignment");

                                    if (ticket2.getOwner() != null) {
                                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Ticket " + finalTicketId + " is already assigned to " + ticket.getOwner().identifier + ". No assignment needed.");
                                    } else {
                                        
                                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Ticket " + finalTicketId + " is unassigned.");
                                        
                                        if (ticket2.getSummary().toLowerCase().contains("re:") || ticket2.getSummary().toLowerCase().contains("compliance: set and review") || ticket2.getSummary().toLowerCase().contains("info systems audits") || 
                                        ticket2.getSummary().toLowerCase().contains("internal system vulnerability") || ticket2.getSummary().toLowerCase().contains("monitor firewall and report") || 
                                        ticket2.getSummary().toLowerCase().contains("routine security check") || ticket2.getSummary().toLowerCase().contains("documentation for review of permissions") || 
                                        ticket2.getSummary().toLowerCase().contains("compliance") || ticket2.getSummary().toLowerCase().contains("audit") || 
                                        ticket2.getContact().getName().toLowerCase().contains(leadContactName.toLowerCase())) {
                                            System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Ticket " + finalTicketId + " appears to be a ticket opened by team for compliance or internal review. Skipping assignment.");
                                            System.out.println("__________________________________________________________________"); // Separator for logs
                                            
                                        // Assign ticket to user and add time entry
                                        } else {
                                            TimeEntry timeEntry = new TimeEntry();

                                            timeEntry.setTicketId(finalTicketId);
                                            timeEntry.setDetailDescriptionFlag(false);
                                            timeEntry.setInternalAnalysisFlag(true);
                                            timeEntry.setResolutionFlag(false);
                                            timeEntry.setTimeStart(connectwiseService.getCurrentTimeForPayload());
                                            timeEntry.setActualHours(String.valueOf(0.0));
                                            timeEntry.setTimeEnd(null);
                                            timeEntry.setInfo(null);
                                            timeEntry.setNotes("Assigned / amatos / ");
                                            timeEntry.setEmailCcFlag(false);
                                            timeEntry.setEmailContactFlag(false);
                                            timeEntry.setEmailResourceFlag(false);
                                            
                                            connectwiseService.assignTicketTo(userId, userIdentifier, finalTicketId);
                                            connectwiseService.addTimeEntryToTicket(finalCompanyId, String.valueOf(finalTicketId), timeEntry);
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                
                            }).start();
                        }

                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Finished processing event for ticketId " + ticketId + " - " + ticket.getSummary());
                        System.out.println("__________________________________________________________________"); // Separator for logs

                        return ResponseEntity.ok("Processed new ticket event for ticketId: " + ticketId);
                    } else {
                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Ignoring ConnectWise event action: " + payload.get("Action"));
                        System.out.println("__________________________________________________________________"); // Separator for logs
                        return ResponseEntity.ok("Ignored event action: " + payload.get("Action"));
                    }

                } else {
                    System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Failed to fetch ticket " + ticketId);
                    return ResponseEntity.ok("Failed to fetch ticket " + ticketId);
                }
            } else {
                System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Ignoring event from ticket: " + ticketId + " (not from list of companies)");
            }

            System.out.println("__________________________________________________________________"); // Separator for logs
            return ResponseEntity.ok("Received");
        }
    }
    
    
    
}