package com.slackwise.slackwise.controller;

import java.io.IOException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import com.slackwise.slackwise.model.Ticket;
import com.slackwise.slackwise.service.SlackService;
import com.slackwise.slackwise.service.ConnectwiseService;
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

    private ArrayList<String> companies = new ArrayList<String>();
    
    // Depreceated: now using database to track which tickets have been posted to Slack
    private Set<Integer> openTicketList = new HashSet<>();

    // add a simple lock object to serialize onNewEvent() calls
    private final Object eventLock = new Object();

    

    /**
     * Initializes the ticket service by fetching all open tickets for a specific company.
     * This method is called after the bean is constructed.
     */
    @PostConstruct
    public void initTickets() {

        companies.add("19300"); // Test company ID, add more if you want to track multiple companies
        companies.add("250");

        try {
            // Replace with your actual company ID if needed
            for (String companyId : companies) {
                List<Ticket> tickets = getOpenTicketsByCompanyId((String)companyId);
                System.out.println("Initialized with " + tickets.size() + " open tickets for company ID: " + companyId);
            }
        } catch (Exception e) {
            e.printStackTrace();
            connectwiseService.tickets = null;
        }
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
            // avoids race conition when creating a new ticket
            if (payload.get("Action").equals("updated")) {
                System.out.println("Waiting 5 seconds on updated thread...");
                try {
                    // Sleep current thread for 5 seconds
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            System.out.println("Received ConnectWise "+ payload.get("Action") + " event for recordId: " + recordId);

            ObjectMapper mapper = new ObjectMapper();

            /*
                Get ticket ID from payload
            */ 
            Integer ticketId = -1;

            if (payload.containsKey("ID")) {
                ticketId = Integer.valueOf(String.valueOf(payload.get("ID")));
                System.out.println("Extracted ticket ID: " + ticketId);
            } else  {
                System.out.println("No ticket ID found in payload");
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
                    entity = mapper.readValue(entityJson, Map.class);
                    System.out.println("Extracted entity from payload");
                }

                if (entity == null) {
                    System.out.println("Entity is null (something has been deleted). Skipping processing for ticket ID: " + recordId);
                    System.out.println("__________________________________________________________________"); // Separator for logs
                    return ResponseEntity.badRequest().body("No Entity found in payload");
                }

                // Extract companyId
                Map<String, Object> company = (Map<String, Object>) entity.get("company");
                
                if (company != null && company.get("id") != null) {
                    companyId = String.valueOf(company.get("id"));
                    System.out.println("Extracted companyId: " + companyId);
                }

            } else {
                System.out.println("No Entity found in payload. (This is likely not a ticket.)");
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

                    System.out.println("Posting new Slack message for ticket: " + ticketId + " - " + ticket.getSummary());
                    slackService.postNewTicket(ticketId.toString(), ticket.getSummary());

                    System.out.println("Updating Slack Thread for new ticket " + ticketId);
                    slackService.updateTicketThread(ticketId.toString(), ticket.getDiscussion(), ticket.getSummary());

                    System.out.println("Finished processing event for ticketId " + ticketId + " - " + ticket.getSummary());
                    System.out.println("__________________________________________________________________"); // Separator for logs
                    return ResponseEntity.ok("Processed new ticket event for ticketId: " + ticketId);
                    

                } else {
                    System.out.println("Failed to fetch ticket " + ticketId);
                    return ResponseEntity.ok("Failed to fetch ticket " + ticketId);
                }
            } else {
                System.out.println("Ignoring event from ticket: " + ticketId + " (not from list of companies)");
            }

            System.out.println("__________________________________________________________________"); // Separator for logs
            return ResponseEntity.ok("Received");
        }
    }
    
    
}