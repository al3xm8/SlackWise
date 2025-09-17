package com.slackwise.slackwise.controller;

import java.io.IOException;
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

import com.slackwise.slackwise.model.Contact;
import com.slackwise.slackwise.model.Note;
import com.slackwise.slackwise.model.Ticket;
import com.slackwise.slackwise.model.TimeEntry;
import com.slackwise.slackwise.service.SlackService;
import com.slackwise.slackwise.service.ConnectwiseService;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;


@RestController
@RequestMapping("/api/connectwise")
public class ConnectwiseController {

    @Autowired
    ConnectwiseService ticketService;

    @Autowired
    SlackService slackService;

    // Store open ticket IDs
    private Set<Integer> openTicketList = new HashSet<>();

    

    /**
     * Initializes the ticket service by fetching all open tickets for a specific company.
     * This method is called after the bean is constructed.
     */
    @PostConstruct
    public void initTickets() {
        try {
            // Replace with your actual company ID if needed
            ticketService.tickets = getOpenTicketsByCompanyId("19300");
        } catch (Exception e) {
            e.printStackTrace();
            ticketService.tickets = null;
        }
    }

    /**
     * Fetches a ticket by its ID.
     *
     * @param companyId The ID of the company.
     * @param ticketId  The ID of the ticket.
     * @return The ticket object.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the request is interrupted.
     */
    @GetMapping("/tickets/{companyId}/{ticketId}")
    public Ticket getTicketById(@PathVariable String companyId, @PathVariable String ticketId) throws IOException, InterruptedException {

        Ticket ticket = ticketService.fetchTicketById(companyId, ticketId);
        return ticket;
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

        List<Ticket> tickets = ticketService.fetchOpenTicketsByCompanyId(companyId);
        System.out.println("Fetched " + tickets.size() + " tickets for company ID: " + companyId);

        return tickets;
    }

    /**
     * Adds a note to a specific ticket.
     * 
     * @param companyId
     * @param ticketId
     * @param note
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @PostMapping("/tickets/{companyId}/{ticketId}/note")
    public String addNoteToTicket(@PathVariable String companyId, @PathVariable String ticketId, @RequestBody Note note) throws IOException, InterruptedException {
        
        // Populate with default values, 
        // TODO: Change to use authenticated user info instead of hardcoded values
        note.setContact(new Contact(4623, "Alex Matos"));

        // Add note to ticket
        String ticketUpdate = ticketService.addNoteToTicket(companyId, ticketId, note);
        
        return ticketUpdate;
    }

    /**
     * Adds a time entry to a specific ticket and creates a corresponding note.
     * 
     * @param companyId
     * @param ticketId
     * @param timeEntry
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    @PostMapping("/tickets/{companyId}/{ticketId}/time-entry")
    public String addTimeEntryToTicket(@PathVariable String companyId, @PathVariable String ticketId, @RequestBody TimeEntry timeEntry) throws IOException, InterruptedException {
        // TODO: Make this ticket feature work, find out what is missing and what needs to be worked on
        String tickeTetUpdate = ticketService.addTimeEntryToTicket(companyId, ticketId, timeEntry);

        // Create a Note object from the TimeEntry and add it to the ticket
        Note note = new Note();
        note.setText(timeEntry.getNote());
        note.setDetailDescriptionFlag(timeEntry.isDetailDescriptionFlag());
        note.setInternalAnalysisFlag(timeEntry.isInternalAnalysisFlag());
        note.setResolutionFlag(timeEntry.isResolutionFlag());

        Contact c = new Contact();
        c.setId(timeEntry.getMemberId());
        c.setName(timeEntry.getMemberName());
        note.setContact(c);

        note.setDateCreated(timeEntry.getDateEntered());
        note.setTimeStart(timeEntry.getTimeStart());
        note.setTimeEnd(timeEntry.getTimeEnd()); 
        note.setInfo(timeEntry.getInfo());

        // Add note/time entry to ticket
        String ticketNUpdate = ticketService.addNoteToTicket(companyId, ticketId, note);

        return ticketNUpdate;
    }

    /**
     * Handles ConnectWise ticket events including ticket creation and updates.
     * 
     * @param recordId
     * @param payload
     * @return
     */
    @PostMapping("/events")
    public ResponseEntity<String> onTicketCreated(@RequestParam("recordId") String recordId,@RequestBody Map<String, Object> payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            //String prettyPayload = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
            //System.out.println("Payload:\n" + prettyPayload);
            System.out.println("Received ConnectWise "+ payload.get("Action") + " event for recordId: " + recordId);

            // Get ticket ID from payload
            Integer ticketId = null;
            if (payload.containsKey("ID")) {
                ticketId = Integer.valueOf(String.valueOf(payload.get("ID")));
            }
            
            // Get the Entity object from payload
            if (payload.containsKey("Entity")) {
                String entityJson = String.valueOf(payload.get("Entity"));
                
                Map<String, Object> entity = null;
                if (entityJson != null && !"null".equals(entityJson)) {
                    entity = mapper.readValue(entityJson, Map.class);
                }

                // If no entity, skip processing (no changes made to ticket)
                if (entity == null) {
                    System.out.println("Entity is null (Ticket has not been changed). Skipping processing for ticket ID: " + recordId);
                    return ResponseEntity.ok("Entity is null. Ignored.");
                }

                ticketId = Integer.valueOf(String.valueOf(entity.get("id")));
            }
            // Exit if there is no Entity in payload
            else {
                System.out.println("No Entity found in payload. (This is likely not a ticket.)");
                System.out.println(payload);
                return ResponseEntity.ok("No Entity found in payload. High chance this isnt a ticket.");
            }

            // There is no ticket ID, exit (something is wrong)
            if (ticketId == null) {
                System.out.println("No ticket ID found in payload.");
                System.out.println(payload);
                return ResponseEntity.ok("No ticket ID found in payload.");
            }

            // Check if ticket is in openTicketList
            if (openTicketList.contains(ticketId)) {

                // Fetch ticket and update Slack thread
                System.out.println("Ticket " + ticketId + " is already in openTicketList.");
                Ticket ticket = ticketService.fetchTicketById("19300", ticketId.toString());

                System.out.println("Updating Slack thread for ticket: " + ticketId);
                slackService.updateTicketThread(ticketId.toString(), ticket.getDiscussion());
                System.out.println("Thread updated.");
            

            // If ticket is not in openTicketList, it is a new ticket
            } else {
                
                System.out.println("Ticket " + ticketId + " is NOT in openTicketList. Treating as new ticket.");
                // Fetch ticket details
                Ticket ticket = ticketService.fetchTicketById("19300", ticketId.toString());

                // Only process tickets from company 19300 (FI Consulting)
                if (ticket != null && ticket.getCompany() != null && "19300".equals(String.valueOf(ticket.getCompany().getId()))) {
                    // Add to openTicketList and send new Slack message
                    openTicketList.add(ticketId);
                    System.out.println("Sending new Slack message for ticket: " + ticketId + " - " + ticket.getSummary());
                    slackService.postNewTicket(ticketId.toString(), ticket.getSummary());
                    System.out.println("Updating Slack Thread for new ticket " + ticketId);
                    slackService.updateTicketThread(ticketId.toString(), ticket.getDiscussion());

                    System.out.println("Finished processing event for ticketId " + ticketId + " - " + ticket.getSummary());
                
                } else {
                    // If not from company 19300, ignore ticket
                    System.out.println("Ignoring event from ticket: " + ticketId + " - " + ticket.getSummary() + " (not from company 19300)");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.out.println("__________________________________________________________________"); // Separator for logs

        return ResponseEntity.ok("Received");
    }
    


}
