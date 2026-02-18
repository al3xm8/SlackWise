package com.slackwise.slackwise.controller;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.slack.api.methods.SlackApiException;
import com.slackwise.slackwise.service.AmazonService;
import com.slackwise.slackwise.service.ConnectwiseService;



@RestController
@RequestMapping("/api/slack")
public class SlackController {
    private static final Logger log = LoggerFactory.getLogger(SlackController.class);

    @Autowired
    AmazonService amazonService;

    @Autowired
    ConnectwiseService connectwiseService;
    
    @Value("${company.id}")
    private String tenantId;

    /**
     * Handles incoming Slack events, including URL verification challenges.
     *
     * @param payload The incoming request payload from Slack.
     * @return A ResponseEntity containing the challenge response or a simple "OK" message.
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SlackApiException 
     */
    @PostMapping("/events")
    public ResponseEntity<String> handleSlackEvents(@RequestBody Map<String, Object> payload) throws IOException, InterruptedException, SlackApiException {

        log.info("Received Slack event");

        // Check if this is the initial URL verification challenge
        if ("url_verification".equals(payload.get("type"))) {
            log.info("Slack challenge received");
            return ResponseEntity.ok((String) payload.get("challenge"));
        }

        if (payload.containsKey("event")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) payload.get("event");
            String eventType = event != null ? (String) event.get("type") : null;

            // Ignore messages with a subtype (edits, deletions, etc.) and bot messages to avoid loops
            if (event != null && (event.get("subtype") != null && !event.get("subtype").equals("file_share"))) {
                log.debug("Ignoring Slack event with subtype={}", event.get("subtype"));
                return ResponseEntity.ok("Ignored subtype");
            }
            if (event != null && event.containsKey("bot_id")) {
                log.debug("Ignoring bot event");
                return ResponseEntity.ok("Ignored bot event");
            }

            // Handle user messages (replies in threads)
            if (event != null && "message".equals(eventType)) {
                String text = event.get("text") != null ? String.valueOf(event.get("text")) : null;
                String user = event.get("user") != null ? String.valueOf(event.get("user")) : null;
                String threadTs = event.get("thread_ts") != null ? String.valueOf(event.get("thread_ts")) : null;
                String ts = event.get("ts") != null ? String.valueOf(event.get("ts")) : null;

                String ticketId = amazonService.getTicketIdByThreadTs(tenantId,threadTs != null ? threadTs : ts);
                // Ignore Slack messages created by this app that use the Note-ID/Ticket-ID prefix
                if (text != null && (text.startsWith("🆔"))) {
                    log.debug("Ignoring app-generated Slack message: {}", text);
                    return ResponseEntity.ok("Ignored app-generated message");
                }
                if (ticketId != null) {
                    log.info("Message in thread for ticketId={} from user={}", ticketId, user);

                    // Process the Slack reply asynchronously to avoid blocking the response to Slack (ACK quikckly)
                    new Thread(() -> {
                        try {
                            connectwiseService.addSlackReplyToTicket(tenantId, ticketId, text, event);
                        } catch (Exception e) {
                            log.error("Failed to process Slack reply for ticketId={}", ticketId, e);
                        }
                    }).start();

                } else {
                    log.warn("No ticket found for thread_ts={} or ts={}", threadTs, ts);
                }
                // Return immediately after scheduling async work to ensure Slack receives a quick 200 OK
                return ResponseEntity.ok("OK");
            }
        }

        return ResponseEntity.ok("OK");
    }

    
}
