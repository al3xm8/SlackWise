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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(SlackService.class);



    // Slack client instance
    private final Slack slack = Slack.getInstance();

    @Autowired
    private AmazonService amazonService;

    @Autowired
    private ConnectwiseService connectwiseService;

    @Autowired
    private SlackTokenManager slackTokenManager;

     /**
     * Handles posting a NEW ticket to Slack.
     * 
     * @param tenantId
     * @param ticketId
     * @param summary
     * @param slackChannelId
     * @return response from Slack API
     * @throws IOException
     * @throws SlackApiException
     * @throws InterruptedException 
     */
    public ChatPostMessageResponse postNewTicket(String tenantId, String ticketId, String summary, String slackChannelId) throws IOException, InterruptedException {
        String slackBotToken = slackTokenManager.getValidBotToken(tenantId);

        if (slackBotToken == null || slackBotToken.isBlank()) {
            log.error("Cannot post ticketId={} to Slack: bot token is missing for tenantId={}", ticketId, tenantId);
            return null;
        }
        if (slackChannelId == null || slackChannelId.isBlank()) {
            log.error("Cannot post ticketId={} to Slack: channel ID is missing for tenantId={}", ticketId, tenantId);
            return null;
        }

        // Attempt to create the ticket item if it doesn't exist yet. This avoids races where multiple
        // processes try to post the top-level Slack message concurrently.
        if (amazonService.createTicketItem(tenantId, ticketId, "") == false) {
            // If a previous attempt failed, the item may exist with an empty ts_thread.
            // In that case we should retry posting to Slack instead of skipping forever.
            Map<String, AttributeValue> existingTicket = amazonService.getTicket(tenantId, ticketId);
            String existingThreadTs = existingTicket != null && existingTicket.containsKey("ts_thread")
                ? existingTicket.get("ts_thread").s()
                : null;

            if (existingThreadTs != null && !existingThreadTs.isBlank()) {
                log.info("Ticket item already has thread_ts for ticketId={}, skipping Slack post", ticketId);
                return null;
            }

            log.info("Ticket item exists without thread_ts for ticketId={}, retrying Slack post", ticketId);
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

            if (response == null || !response.isOk()) {
                String slackError = response != null ? response.getError() : "unknown_error";
                log.error("Failed posting new ticket to Slack for ticketId={} tenantId={} error={}", ticketId, tenantId, slackError);
                return null;
            }

            String postedTs = response.getTs();
            if (postedTs == null || postedTs.isBlank()) {
                log.error("Slack post for ticketId={} tenantId={} succeeded without ts; skipping thread_ts update", ticketId, tenantId);
                return null;
            }

            // Attempt to set the thread_ts in DynamoDB. If this fails, it means another process
            // has already set it, so we delete the duplicate Slack message we just posted.
            if (!amazonService.setThreadTs(tenantId, ticketId, postedTs)) {
                slack.methods(slackBotToken).chatDelete(req -> req
                        .channel(slackChannelId)
                        .ts(postedTs)
                );

                log.info("Another process set ts_thread for ticketId={}, deleted duplicate Slack message", ticketId);
                return null;
            }

            // Update the ticket item with the posted thread_ts
            amazonService.updateThreadTs(tenantId, ticketId, postedTs);

            return response;

        } catch (SlackApiException e) {
            log.error("Failed posting new ticket to Slack for ticketId={}", ticketId, e);
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
    /**
     * Update Slack thread for a ticket with new notes
     * 
     * @param tenantId
     * @param ticketId
     * @param discussion
     * @param summary
     * @param slackChannelId
     * @return list of Slack responses for each posted note
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SlackApiException 
     */
    public List<ChatPostMessageResponse> updateTicketThread(String tenantId, String ticketId, List<Note> discussion, String summary, String slackChannelId) throws IOException, InterruptedException, SlackApiException {
        String slackBotToken = slackTokenManager.getValidBotToken(tenantId);

        if (slackBotToken == null || slackBotToken.isBlank()) {
            log.error("Cannot update thread for ticketId={} because Slack bot token is missing for tenantId={}", ticketId, tenantId);
            return null;
        }
        if (slackChannelId == null || slackChannelId.isBlank()) {
            log.error("Cannot update thread for ticketId={} because Slack channel ID is missing for tenantId={}", ticketId, tenantId);
            return null;
        }
        if (discussion == null || discussion.isEmpty()) {
            return new ArrayList<>();
        }

        // Implement logic to update the Slack thread for the ticket
        // This could involve searching for the original message and posting a reply
        List<ChatPostMessageResponse> responses = new ArrayList<>();

        // Fetch thread_ts and posted notes from DynamoDB
        Map<String, AttributeValue> ticket = amazonService.getTicket(tenantId, ticketId);

        // If Dynamo lookup fails, log and continue to attempt posting (fail open)
        String resolvedThreadTs = ticket != null && ticket.containsKey("ts_thread") ? ticket.get("ts_thread").s() : null;

        // If we don't have a thread_ts, post a new top-level message first
        // This can happen if the initial ticket creation event failed to post to Slack
        // or if we missed that event entirely.
        // If posting the new ticket fails, abort the note posting. Don't want to spam Slack.
        if (resolvedThreadTs == null || resolvedThreadTs.isBlank()) {
            log.warn("No thread_ts found for ticketId={}, posting top-level message", ticketId);
            postNewTicket(tenantId, ticketId, summary, slackChannelId);
            ticket = amazonService.getTicket(tenantId, ticketId);

            String tsThread2 = ticket != null && ticket.containsKey("ts_thread") ? ticket.get("ts_thread").s() : null;
            if (tsThread2 == null || tsThread2.isBlank()) {
                log.error("Failed to retrieve thread_ts after posting ticketId={}, aborting note posting", ticketId);
                return null;
            }
            resolvedThreadTs = tsThread2;
        }
        final String threadTsToUse = resolvedThreadTs;

        
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
                    
                    log.debug("Posting note text for ticketId={} noteId={}", ticketId, noteIdStr);

                    ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                            .channel(slackChannelId)
                            .text("🆔 " + note.getId() + "   👤 " + contactName + "\n\n" + slackNoteText)
                            .threadTs(threadTsToUse)
                            .mrkdwn(true)
                    );

                    // If posting failed, log and skip adding to responses
                    if (!response.isOk()) {
                        log.error("Failed to post note to Slack for ticketId={} noteId={}: {}", ticketId, noteIdStr, response.getError());
                        return null;

                    // If successful, add to responses and save note in DynamoDB
                    } else {
                        responses.add(response);
                        // Save noteId and ts in DynamoDB
                        amazonService.addNoteToTicket(tenantId, ticketId, noteIdStr, response.getTs());
                        log.info("Posted noteId={} to Slack for ticketId={}", noteIdStr, ticketId);
                    }
                } catch (IOException | SlackApiException e) {
                    log.error("Failed posting note to Slack for ticketId={} noteId={}", ticketId, noteIdStr, e);
                    return null;
                }
            }
        }

        return responses;
    }
    
    
}
