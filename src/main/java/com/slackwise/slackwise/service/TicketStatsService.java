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
     * Aggregate ticket statistics for dashboard across all tracked company IDs.
     *
     * @param trackedCompanyIds ConnectWise company IDs/identifiers to include
     * @return Map with stats: averageResponseTime, totalTicketsOpen, pendingClientResponse, pendingInternalResponse
     */
    public Map<String, Object> getTicketStats(List<String> trackedCompanyIds) {
        Map<String, Object> stats = new HashMap<>();

        int pendingClientResponse = 0;
        int pendingInternalResponse = 0;
        int openTickets = 0;

        int responseCount = 0;
        double totalResponseTime = 0;

        HashMap<String, Integer> ticketStatuses = new HashMap<>();
        HashMap<String, Integer> ticketContacts = new HashMap<>();
        HashMap<String, Integer> ticketBoards = new HashMap<>();

        try {
            List<Ticket> tickets = connectwiseService.fetchOpenTicketsByCompanyIds(trackedCompanyIds);
            openTickets = tickets.size();

            for (Ticket ticket : tickets) {
                String boardName = ticket.getBoardName();
                ticketBoards.put(boardName, ticketBoards.getOrDefault(boardName, 0) + 1);

                if (ticket.getStatus() != null && ticket.getStatus().name != null) {
                    ticketStatuses.put(ticket.getStatus().name, ticketStatuses.getOrDefault(ticket.getStatus().name, 0) + 1);
                }

                String contactName = ticket.getContact() != null ? ticket.getContact().getName() : null;
                if (contactName != null && !contactName.isBlank()) {
                    ticketContacts.put(contactName, ticketContacts.getOrDefault(contactName, 0) + 1);
                }

                if (ticket.getDiscussion() != null && !ticket.getDiscussion().isEmpty()) {
                    Note lastEntry = ticket.getDiscussion().get(ticket.getDiscussion().size() - 1);
                    if (isInternalEntry(lastEntry, ticket)) {
                        pendingClientResponse++;
                    } else {
                        pendingInternalResponse++;
                    }
                    responseCount++;
                } else {
                    pendingInternalResponse++;
                }

                totalResponseTime += ticket.getActualHours();
            }
        } catch (IOException | InterruptedException e) {
            log.error("Error fetching ticket stats for trackedCompanyIds={}", trackedCompanyIds, e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
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

    private boolean isInternalEntry(Note entry, Ticket ticket) {
        if (entry == null) {
            return false;
        }

        if (entry.getMember() != null && entry.getMember().getName() != null && !entry.getMember().getName().isBlank()) {
            return true;
        }

        if (entry.isInternalAnalysisFlag() || entry.isResolutionFlag()) {
            return true;
        }

        String ticketContactName = ticket.getContact() != null ? ticket.getContact().getName() : null;
        String entryContactName = entry.getContact() != null ? entry.getContact().getName() : null;

        if (entryContactName == null || entryContactName.isBlank()) {
            return true;
        }

        if (ticketContactName == null || ticketContactName.isBlank()) {
            return false;
        }

        return !entryContactName.trim().equalsIgnoreCase(ticketContactName.trim());
    }
}
