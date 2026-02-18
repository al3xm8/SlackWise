package com.slackwise.slackwise.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.slackwise.slackwise.model.Note;
import com.slackwise.slackwise.model.Ticket;

@Service
public class TicketStatsService {
    private static final Logger log = LoggerFactory.getLogger(TicketStatsService.class);

    @Autowired
    private ConnectwiseService connectwiseService;

    /**
     * Aggregate ticket statistics for dashboard.
     * @param tenantId
     * @return Map with stats: averageResponseTime, totalTicketsOpen, closedTodayCount, ticketsInProgress
     */
    public Map<String, Object> getTicketStats(String tenantId) {
        Map<String, Object> stats = new HashMap<>();

        int pendingClientResponse = 0;
        int pendingInternalResponse = 0;
        int openTickets = 0;
        
        double avgResponseTime = 0;
        int responseCount = 0;
        
        double totalResponseTime = 0;
        
        HashMap<String, Integer> ticketStatuses = new HashMap<>();
        HashMap<String, Integer> ticketContacts = new HashMap<>();
        HashMap<String, Integer> ticketBoards = new HashMap<>();
        
        
        
        try {
            List<Ticket> tickets = connectwiseService.fetchOpenTicketsByCompanyId(tenantId);
            // For simplicity, we'll just count open tickets and pending responses here.
            openTickets = tickets.size();
            
            for (Ticket ticket : tickets) {
                String boardName = ticket.getBoardName();
                ticketBoards.put(boardName, ticketBoards.getOrDefault(boardName, 0) + 1);
                
                // Check the last time entry to determine if we're waiting on a client or internal response
                if (ticket.getDiscussion() == null || ticket.getDiscussion().isEmpty()) {
                    // If there are no discussions, we can assume it's waiting on a client response
                    pendingClientResponse++;
                    continue;
                }
                
                Note lastEntry = ticket.getDiscussion().get(ticket.getDiscussion().size() - 1);
                
                if (lastEntry.getMember() != null ) {
                    
                    if (lastEntry.getMember().getName().equalsIgnoreCase(ticket.getContact().getName())){
                        pendingInternalResponse++;
                        responseCount++;
                    }
                    else {
                        pendingClientResponse++;
                        responseCount++;
                    }

                } else if (lastEntry.getContact() != null) {
                    
                    if (lastEntry.getContact().getName().equalsIgnoreCase(ticket.getContact().getName())){
                        pendingInternalResponse++;
                        responseCount++;
                    }
                    else {
                        pendingClientResponse++;
                        responseCount++;
                    }
                    
                } else {
                    // If we can't determine who made the last entry, we'll skip response time calculation for this ticket
                    continue;
                }
                
                
                // Organize ticket statuses for dashboard display
                if (ticketStatuses.containsKey(ticket.getStatus().name)) {
                    ticketStatuses.put(ticket.getStatus().name, ticketStatuses.get(ticket.getStatus().name) + 1);
                } else {
                    ticketStatuses.put(ticket.getStatus().name, 1);
                }
                
                // Organize ticket contacts for dashboard display
                if (ticketContacts.containsKey(ticket.getContact().getName())) {
                    ticketContacts.put(ticket.getContact().getName(), ticketContacts.get(ticket.getContact().getName()) + 1);
                } else {
                    ticketContacts.put(ticket.getContact().getName(), 1);
                }
                
                // Calculate average response time based on time entries
                totalResponseTime += ticket.getActualHours();
                
            }

        } catch (IOException | InterruptedException e) {
            log.error("Error fetching ticket stats for tenantId={}", tenantId, e);
        }

        double avgResponse = responseCount > 0 ? totalResponseTime / responseCount : 0;
        stats.put("averageResponseTime", String.format("%.2f hours", avgResponse));
        stats.put("totalTicketsOpen", openTickets);
        stats.put("pendingClientResponse", pendingClientResponse);
        stats.put("pendingInternalResponse", pendingInternalResponse);
        stats.put("ticketStatuses", ticketStatuses);
        stats.put("ticketContacts", ticketContacts);
        stats.put("ticketBoards", ticketBoards);
        return stats;
    }
}
