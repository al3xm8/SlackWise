package com.slackwise.slackwise.service;

import com.slackwise.slackwise.model.Note;
import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SlackService {

    // Slack configuration properties
    @Value("${slack.bot.token}")
    private String slackBotToken;

    @Value("${slack.channel.id}")
    private String slackChannelId;

    // Slack client instance
    private final Slack slack = Slack.getInstance();

    // Store open ticket IDs
    //private Map<Integer, String> noteIdTothreadTS = new HashMap<>();
    
    @Autowired
    private AmazonService amazonService;

    @Autowired
    private ConnectwiseService ticketService;

    /**
     * Handle posting a new ticket to Slack.
     * 
     * 
     * @param ticketId
     * @param summary
     * @return response from Slack API
     * @throws IOException
     * @throws SlackApiException
     * @throws InterruptedException 
     */
    public ChatPostMessageResponse postNewTicket(String ticketId, String summary) throws IOException, SlackApiException, InterruptedException {

        // Attempt to create the ticket item if it doesn't exist yet. This avoids races where multiple
        // processes try to post the top-level Slack message concurrently.
        try {
            amazonService.createTicketItem(ticketId, "");
        } catch (Exception e) {
            System.out.println("Warning: failed to create DynamoDB ticket item:\n" + e.getMessage());
        }

        // If the ticket item already existed and already has a thread_ts, return early.
        try {
            Map<String, software.amazon.awssdk.services.dynamodb.model.AttributeValue> existing = amazonService.getTicket(ticketId);
            if (existing != null && existing.containsKey("ts_thread") && existing.get("ts_thread").s() != null && !existing.get("ts_thread").s().isBlank()) {
                System.out.println("Ticket " + ticketId + " already has a Slack thread ts (" + existing.get("ts_thread").s() + "). Skipping postNewTicket.");
                return null;
            }
        } catch (Exception e) {
            // If Dynamo lookup fails, log and continue to attempt posting (fail open)
            System.out.println("Warning: failed to read existing ticket from DynamoDB for id " + ticketId + ":\n" + e.getMessage());
        }
        // Get contact name that is associated with ticket
        String contactName = ticketService.getContactName(ticketId);
        
        // Post to Slack
        ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                .channel(slackChannelId)
                .text("🆔: " + ticketId + "\n👤: " + contactName + "\nSummary📝: " + summary)
        );

        if (!response.isOk()) {
            System.out.println("Failed to post to Slack:\n" + response.getError());
            return response;
        }

        String postedTs = response.getTs();

        // Try to set the thread ts only if it is still missing. If another process set it first, delete our message.
        boolean set = false;
        try {
            set = amazonService.setThreadTs(ticketId, postedTs);
        } catch (Exception e) {
            System.out.println("Warning: failed to set thread ts in DynamoDB for ticket " + ticketId + ": " + e.getMessage());
        }

        if (!set) {
            // Another process won the race. Delete the message we just posted to avoid duplicate top-level messages.
            try {
                slack.methods(slackBotToken).chatDelete(req -> req
                    .channel(slackChannelId)
                    .ts(postedTs)
                );
            } catch (Exception e) {
                System.out.println("Warning: failed to delete duplicate Slack message: " + e.getMessage());
            }
            return null;
        }

        // We successfully set the thread ts; ensure the item is persisted with notes empty list
        try {
            amazonService.updateThreadTs(ticketId, postedTs);
        } catch (Exception e) {
            System.out.println("Warning: failed to persist thread ts in DynamoDB for ticket " + ticketId + ": " + e.getMessage());
        }

        return response;
    }

    /**
     * When posting a new note
     * 
     * @param ticketId
     * @param noteId
     * @param noteText
     * @return 
     * @throws IOException
     * @throws SlackApiException
     */
    public ChatPostMessageResponse postNote(String ticketId, String noteId, String noteText) throws IOException, SlackApiException {
        
        // Get thread timestamp from DynamoDB
        Map<String, AttributeValue> ticket = amazonService.getTicket(ticketId);
        String tsThread = ticket.get("ts_thread").s();

        // Post to Slack in the designated thread
        ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                .channel(slackChannelId)
                .text("🆔: " + noteId + "\nNote: " + noteText)
                .threadTs(tsThread)
        );

        if (response.isOk()) {
            // Save noteId and ts in DynamoDB
            amazonService.addNoteToTicket(
                ticketId,
                noteId,
                response.getTs()
            );
        } else {
            System.out.println("Failed to post note to Slack: " + response.getError());
        }

        return response;
    }

    
    /**
     * Update Slack thread for a ticket with new notes
     * 
     * @param ticketId
     * @param discussion
     * @return list of Slack responses for each posted note
     * @throws InterruptedException 
     * @throws IOException 
     */
    public List<ChatPostMessageResponse> updateTicketThread(String ticketId, List<Note> discussion) throws IOException, InterruptedException {
        
        // Implement logic to update the Slack thread for the ticket
        // This could involve searching for the original message and posting a reply
        List<ChatPostMessageResponse> responses = new java.util.ArrayList<>();
        
        // Always fetch thread_ts and posted notes from DynamoDB
        Map<String, AttributeValue> ticket = amazonService.getTicket(ticketId);

        // If Dynamo lookup fails, log and continue to attempt posting (fail open)
        String tsThread = ticket != null && ticket.containsKey("ts_thread") ? ticket.get("ts_thread").s() : null;
        
        // Keep track of already posted note IDs to avoid duplicates
        // If ticket or notes are null, postedNoteIds will remain empty
        Set<String> postedNoteIds = new HashSet<>();
        if (ticket != null && ticket.containsKey("notes")) {
            List<AttributeValue> notesArr = ticket.get("notes").l();
            // Extract note IDs into a set for quick lookup
            for (AttributeValue av : notesArr) {
                // Defensive check in case of malformed data
                if (av.m().containsKey("noteId")) {
                    postedNoteIds.add(av.m().get("noteId").s());
                }
            }
        }

        // If we don't have a thread_ts, log and post as top-level message
        if (tsThread == null) {
            System.out.println("No thread_ts found for ticket " + ticketId + ". Posting as top-level message.");
        }
        // Post each note that hasn't been posted yet
        for (Note note : discussion) {
            String noteIdStr = String.valueOf(note.getId());

            String contactName = ticketService.getContactName(ticketId, note.getId());

            // Only post if this note ID hasn't been posted yet
            if (!postedNoteIds.contains(noteIdStr)) {
                try {
                    ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                            .channel(slackChannelId)
                            .text("🆔:" + note.getId() + "\n👤: " + contactName + "\n\n" + note.getText() + "\n_________________________________")
                            .threadTs(tsThread)
                    );

                    // If posting failed, log and skip adding to responses
                    if (!response.isOk()) {
                        System.out.println("Failed to post to Slack: " + response.getError());
                        return null;

                    // If successful, add to responses and save note in DynamoDB
                    } else {
                        responses.add(response);
                        // Save noteId and ts in DynamoDB
                        amazonService.addNoteToTicket(ticketId, noteIdStr, response.getTs());
                        System.out.println("Posted note " + noteIdStr + " to Slack for ticket " + ticketId);
                    }
                } catch (IOException | SlackApiException e) {
                    e.printStackTrace();
                    return null;
                }
            }
        }

        return responses;
    }
}
