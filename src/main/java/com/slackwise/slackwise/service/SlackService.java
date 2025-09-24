package com.slackwise.slackwise.service;

import com.slackwise.slackwise.model.Note;

import java.util.regex.Matcher;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slack.api.model.block.ImageBlock;
import com.slack.api.model.block.LayoutBlock;
import com.slack.api.model.block.SectionBlock;
import com.slack.api.model.block.composition.MarkdownTextObject;

import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

@Service
public class SlackService {

    // Slack configuration properties
    @Value("${slack.bot.token}")
    private String slackBotToken;

    @Value("${slack.channel.id}")
    private String slackChannelId;

    // Slack client instance
    private final Slack slack = Slack.getInstance();

    @Autowired
    private AmazonService amazonService;

    @Autowired
    private ConnectwiseService connectwiseService;

     /**
     * Handle posting a NEW ticket to Slack.
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

        if (amazonService.createTicketItem(ticketId, "") == false) {
            // Ticket item already exists, so another process has already posted the Slack message.
            System.out.println("Ticket item already exists for ticketId: " + ticketId + ", skipping Slack post.");
            return null;
        }

        // Get the contact name for new ticket
        String contactName = connectwiseService.getContactNameByTicketId(ticketId);

        // Post to Slack
        ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                .channel(slackChannelId)
                .text("🆔" + ticketId + "\n👤" + contactName + "\n📝: " + summary)
        );

        if (!response.isOk()) {
            System.out.println("Failed to post to Slack:\n" + response.getError());
            return response;
        }

        String postedTs = response.getTs();

        if (!amazonService.setThreadTs(ticketId, postedTs)) {
            slack.methods(slackBotToken).chatDelete(req -> req
                    .channel(slackChannelId)
                    .ts(postedTs)
            );

            System.out.println("Another process set ts_thread for ticketId: " + ticketId + ", deleted duplicate Slack message.");  
            return null;
        }

        amazonService.updateThreadTs(ticketId, postedTs);

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

        // Delay in case of concurrent events (avoids race conditions)
        Thread.sleep(500);

        // Fetch thread_ts and posted notes from DynamoDB
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
            String contactName = note.getContact() != null ? note.getContact().getName() : note.getMember().getName();

            // Only post if this note ID hasn't been posted yet
            if (!postedNoteIds.contains(noteIdStr)) {
                try {
                    // TODO: Parse the text for picure links and add them to the bot response

                    ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                            .channel(slackChannelId)
                            .text("🆔" + note.getId() + "\n👤 " + contactName + "\n\n" + note.getText() + "\n_________________________________")
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