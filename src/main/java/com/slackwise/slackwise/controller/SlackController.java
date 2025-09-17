package com.slackwise.slackwise.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slackwise.slackwise.service.AmazonService;
import com.slackwise.slackwise.service.ConnectwiseService;

@RestController
@RequestMapping("/api/slack")
public class SlackController {

    @Autowired
    AmazonService amazonService;

    @Autowired
    ConnectwiseService ticketService;

    /**
     * Handles incoming Slack events, including URL verification challenges.
     *
     * @param payload The incoming request payload from Slack.
     * @return A ResponseEntity containing the challenge response or a simple "OK" message.
     */
    @PostMapping("/events")
    public ResponseEntity<String> handleSlackEvents(@RequestBody Map<String, Object> payload) {

        //System.out.println("Received Slack event: " + payload);

        // Check if this is the initial URL verification challenge
        if ("url_verification".equals(payload.get("type"))) {
            System.out.println("Slack challenge received!");
            return ResponseEntity.ok((String) payload.get("challenge"));
        }

        if (payload.containsKey("event")) {
            Map<String, Object> event = (Map<String, Object>) payload.get("event");
            String eventType = event != null ? (String) event.get("type") : null;

            // Ignore messages with a subtype (edits, deletions, etc.) and bot messages to avoid loops
            if (event != null && event.get("subtype") != null) {
                System.out.println("Ignoring event with subtype: " + event.get("subtype"));
                return ResponseEntity.ok("Ignored subtype");
            }
            if (event != null && event.containsKey("bot_id")) {
                System.out.println("Ignoring bot event: " + event);
                return ResponseEntity.ok("Ignored bot event");
            }

            // Handle user messages (replies in threads)
            if (event != null && "message".equals(eventType)) {
                String text = event.get("text") != null ? String.valueOf(event.get("text")) : null;
                String user = event.get("user") != null ? String.valueOf(event.get("user")) : null;
                String threadTs = event.get("thread_ts") != null ? String.valueOf(event.get("thread_ts")) : null;
                String ts = event.get("ts") != null ? String.valueOf(event.get("ts")) : null;

                String ticketNumber = amazonService.getTicketIdByThreadTs(threadTs != null ? threadTs : ts);
                // Ignore Slack messages created by this app that use the Note-ID/Ticket-ID prefix
                if (text != null && (text.startsWith("Note-ID:") || text.startsWith("Ticket-ID:"))) {
                    System.out.println("Ignoring app-generated Slack message: " + text);
                    return ResponseEntity.ok("Ignored app-generated message");
                }
                if (ticketNumber != null) {
                    System.out.println("Message in thread for ticket " + ticketNumber + ": " + text + " from user " + user);
                    ticketService.addSlackReplyToTicket(ticketNumber, text, event);
                } else {
                    System.out.println("No ticket found for thread_ts: " + threadTs + " or ts: " + ts);
                }
            }
        }

        return ResponseEntity.ok("OK");
    }

    
}
