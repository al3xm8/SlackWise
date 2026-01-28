package com.slackwise.slackwise.service;

import com.slackwise.slackwise.model.Note;
import com.slackwise.slackwise.model.Tenant;
import com.slackwise.slackwise.model.Ticket;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.ImageBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;
import com.slackwise.slackwise.util.TextFormatTranslator;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@Service
public class SlackService {



    // Slack client instance
    private final Slack slack = Slack.getInstance();

    @Autowired
    private AmazonService amazonService;

    @Autowired
    private ConnectwiseService connectwiseService;

     /**
     * Handles posting a NEW ticket to Slack.
     * 
     * @param ticketId
     * @param summary
     * @return response from Slack API
     * @throws IOException
     * @throws SlackApiException
     * @throws InterruptedException 
     */
    public ChatPostMessageResponse postNewTicket(String tenantId, String ticketId, String summary, String slackChannelId, String slackBotToken) throws IOException, InterruptedException {


        // Attempt to create the ticket item if it doesn't exist yet. This avoids races where multiple
        // processes try to post the top-level Slack message concurrently.
        if (amazonService.createTicketItem(tenantId, ticketId, "") == false) {
            // Ticket item already exists, so another process has already posted the Slack message.
            System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Ticket item already exists for ticketId: " + ticketId + ", skipping Slack post.");
            return null;
        }

        // Get the contact name for a new ticket
        String contactName = "N\\A";
        if (connectwiseService.getContactNameByTicketId(ticketId) == null) {
            Ticket ticket = connectwiseService.fetchTicketById("", ticketId);
            if (ticket.getContact() != null) {
                contactName = connectwiseService.getContactNameByTicketId(ticketId);
            }
        } else {
            contactName = connectwiseService.getContactNameByTicketId(ticketId);
        }

        final String finalContactName = contactName;
        String slackSummary = summary != null ? TextFormatTranslator.connectwiseToSlack(summary) : "";

        try {

            // Post to Slack
            ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                    .channel(slackChannelId)
                    .text("🆔" + ticketId + "    👤" + finalContactName + "\n📝: " + slackSummary)
                    .mrkdwn(true)
            );

            String postedTs = response.getTs();

            // Attempt to set the thread_ts in DynamoDB. If this fails, it means another process
            // has already set it, so we delete the duplicate Slack message we just posted.
            if (!amazonService.setThreadTs(tenantId, ticketId, postedTs)) {
                slack.methods(slackBotToken).chatDelete(req -> req
                        .channel(slackChannelId)
                        .ts(postedTs)
                );

                System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Another process set ts_thread for ticketId: " + ticketId + ", deleted duplicate Slack message.");  
                return null;
            }

            // Update the ticket item with the posted thread_ts
            amazonService.updateThreadTs(tenantId, ticketId, postedTs);

            return response;

        } catch (SlackApiException e) {
            e.printStackTrace();
            return null;

        }

        
    }

    /**
     * Update Slack thread for a ticket with new notes
     * 
     * @param ticketId
     * @param discussion
     * @return list of Slack responses for each posted note
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SlackApiException 
     */
    public List<ChatPostMessageResponse> updateTicketThread(String tenantId, String ticketId, List<Note> discussion, String summary, String slackChannelId, String slackBotToken) throws IOException, InterruptedException, SlackApiException {

        // Implement logic to update the Slack thread for the ticket
        // This could involve searching for the original message and posting a reply
        List<ChatPostMessageResponse> responses = new java.util.ArrayList<>();

        // Fetch thread_ts and posted notes from DynamoDB
        Map<String, AttributeValue> ticket = amazonService.getTicket(tenantId, ticketId);

        // If Dynamo lookup fails, log and continue to attempt posting (fail open)
        String tsThread = ticket != null && ticket.containsKey("ts_thread") ? ticket.get("ts_thread").s() : null;

        // If we don't have a thread_ts, post a new top-level message first
        // This can happen if the initial ticket creation event failed to post to Slack
        // or if we missed that event entirely.
        // If posting the new ticket fails, abort the note posting. Don't want to spam Slack.
        if (tsThread == null) {
            System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> No thread_ts found for ticket " + ticketId + ". Posting ticket to slack channel.");
            postNewTicket(tenantId, ticketId, summary, slackChannelId, slackBotToken);
            ticket = amazonService.getTicket(tenantId, ticketId);

            String tsThread2 = ticket != null && ticket.containsKey("ts_thread") ? ticket.get("ts_thread").s() : null;
            if (tsThread2 == null) {
                System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Failed to retrieve thread_ts after posting new ticket for ticket " + ticketId + ". Aborting note posting.");
                return null;
            }
        }

        
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

        // Post each note that hasn't been posted yet
        for (Note note : discussion) {
            String noteIdStr = String.valueOf(note.getId());
            String contactName = note.getContact() != null ? note.getContact().getName() : note.getMember().getName();

            // Only post if this note ID hasn't been posted yet
            if (!postedNoteIds.contains(noteIdStr)) {
                try {

                    String noteText = note.getText();
                    String slackNoteText = TextFormatTranslator.connectwiseToSlack(noteText);
                    
                    System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Text: \n" + slackNoteText);

                    ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                            .channel(slackChannelId)
                            .text("🆔 " + note.getId() + "   👤 " + contactName + "\n\n" + slackNoteText)
                            .threadTs(tsThread)
                            .mrkdwn(true)
                    );

                    // If posting failed, log and skip adding to responses
                    if (!response.isOk()) {
                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Failed to post to Slack: " + response.getError());
                        return null;

                    // If successful, add to responses and save note in DynamoDB
                    } else {
                        responses.add(response);
                        // Save noteId and ts in DynamoDB
                        amazonService.addNoteToTicket(tenantId, ticketId, noteIdStr, response.getTs());
                        System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Posted note " + noteIdStr + " to Slack for ticket " + ticketId);
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
