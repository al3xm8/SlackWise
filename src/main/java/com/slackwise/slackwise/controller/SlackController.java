package com.slackwise.slackwise.controller;

import java.io.IOException;
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
    ConnectwiseService connectwiseService;

    /**
     * Handles incoming Slack events, including URL verification challenges.
     *
     * @param payload The incoming request payload from Slack.
     * @return A ResponseEntity containing the challenge response or a simple "OK" message.
     * @throws InterruptedException 
     * @throws IOException 
     */
    @PostMapping("/events")
    public ResponseEntity<String> handleSlackEvents(@RequestBody Map<String, Object> payload) throws IOException, InterruptedException {

        System.out.println("Received Slack event");

        // Check if this is the initial URL verification challenge
        if ("url_verification".equals(payload.get("type"))) {
            System.out.println("Slack challenge received!");
            System.out.println("__________________________________________________________________"); // Separator for logs
            return ResponseEntity.ok((String) payload.get("challenge"));
        }

        if (payload.containsKey("event")) {
            Map<String, Object> event = (Map<String, Object>) payload.get("event");
            String eventType = event != null ? (String) event.get("type") : null;

            // Ignore messages with a subtype (edits, deletions, etc.) and bot messages to avoid loops
            if (event != null && (event.get("subtype") != null && !event.get("subtype").equals("file_share"))) {
                System.out.println("Ignoring event with subtype: " + event.get("subtype"));
                return ResponseEntity.ok("Ignored subtype");
            }
            if (event != null && event.containsKey("bot_id")) {
                System.out.println("Ignoring bot event");
                System.out.println("__________________________________________________________________"); // Separator for logs
                return ResponseEntity.ok("Ignored bot event");
            }

            // Handle user messages (replies in threads)
            if (event != null && "message".equals(eventType)) {
                String text = event.get("text") != null ? String.valueOf(event.get("text")) : null;
                String user = event.get("user") != null ? String.valueOf(event.get("user")) : null;
                String threadTs = event.get("thread_ts") != null ? String.valueOf(event.get("thread_ts")) : null;
                String ts = event.get("ts") != null ? String.valueOf(event.get("ts")) : null;

                String ticketId = amazonService.getTicketIdByThreadTs(threadTs != null ? threadTs : ts);
                // Ignore Slack messages created by this app that use the Note-ID/Ticket-ID prefix
                if (text != null && (text.startsWith("🆔"))) {
                    System.out.println("Ignoring app-generated Slack message: " + text);
                    return ResponseEntity.ok("Ignored app-generated message");
                }
                if (ticketId != null) {
                    System.out.println("Message in thread for ticket " + ticketId + ": " + text + " from user " + user);

                    // Process the Slack reply asynchronously to avoid blocking the response to Slack (ACK quikckly)
                    new Thread(() -> {
                        try {
                            connectwiseService.addSlackReplyToTicket(ticketId, text, event);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                } else {
                    System.out.println("No ticket found for thread_ts: " + threadTs + " or ts: " + ts);
                }
                // Return immediately after scheduling async work to ensure Slack receives a quick 200 OK
                return ResponseEntity.ok("OK");
            }
        }

        return ResponseEntity.ok("OK");
    }

    
}