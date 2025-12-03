package com.slackwise.slackwise.service;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.slackwise.slackwise.model.Tenant;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Service
public class AmazonService {

    // AWS configuration properties
    @Value("${aws.region}")
    private String awsRegion;

    // DynamoDB client
    private DynamoDbClient dynamoDb;

    @Value("${aws.accessKeyId}")
    private String awsAccessKeyId;

    @Value("${aws.secretAccessKey}")
    private String awsSecretAccessKey;

    // DynamoDB table name
    @Value("${aws.dynamodb.table}")
    private String tableName;

    Tenant tenant;

    // Initialize DynamoDB client
    @PostConstruct
    public void init() {
        dynamoDb = DynamoDbClient.builder()
            .region(Region.of(awsRegion))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                )
            )
            .build();
    } 

    /**
     * Store ticket thread_ts, ticketId and noteIds associated with the ticket in 
     * DynamoDB to link them together and.
     * 
     * @param ticketId
     * @param notes
     * @param tsThread
     */
    public void putTicketWithNotes(String ticketId, List<Map<String, String>> notes, String tsThread) {
        // Validate input
        if (ticketId == null || ticketId.isBlank()) throw new IllegalArgumentException("ticketId is null/blank");
        String ticketIdStr = String.valueOf(ticketId);
        
        // Prepare Map to put ticketId and thread ts and link them together
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenant.getTenantId()).build());
        item.put("ticketId", AttributeValue.builder().s(ticketIdStr).build());
        item.put("ts_thread", AttributeValue.builder().s(tsThread).build());

        // Always include notes in Map, even if empty
        item.put("notes", AttributeValue.builder().l(
            notes == null ? List.of() :
            notes.stream()
                .map(note -> AttributeValue.builder().m(Map.of(
                    "noteId", AttributeValue.builder().s(String.valueOf(note.get("noteId"))).build(),
                    "ts",     AttributeValue.builder().s(note.get("ts")).build()
                )).build())
                .collect(java.util.stream.Collectors.toList())
        ).build());

        // Put Map item into DynamoDB
        dynamoDb.putItem(PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build());
    }

    /**
     * Get ticket info (notes and Slack thread). It will be EMPTY if the item does not exist.
     * 
     * @param ticketId
     * @return Map of ticket attributes, or EMPTY map if not found
     */
    public Map<String, AttributeValue> getTicket(String ticketId) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("tenantId", AttributeValue.builder().s(String.valueOf(tenant.getTenantId())).build(),
                        "ticketId", AttributeValue.builder().s(String.valueOf(ticketId)).build()))
            .build());
        // Return the item map directly; it will be EMPTY if the item does not exist
        return response.item();
    }

    /**
     * Add a note to an existing ticket in DynamoDB
     * 
     * @param ticketId
     * @param noteId
     * @param ts
     */
    public void addNoteToTicket(String ticketId, String noteId, String ts) 
    {
        // Get existing ticket info from DynamoDB
        Map<String, AttributeValue> ticket = getTicket(ticketId);

        // Populate notes list; if ticket does not exist, notes will be empty
        List<AttributeValue> notes = ticket.containsKey("notes") ? ticket.get("notes").l() : List.of();

        // Add the new note to the list
        List<AttributeValue> updatedNotes = notes.stream().collect(Collectors.toList());
        updatedNotes.add(AttributeValue.builder().m(Map.of(
            "noteId", AttributeValue.builder().s(noteId).build(),
            "ts", AttributeValue.builder().s(ts).build()
        )).build());

        // Get existing thread_ts; if ticket does not exist, it will be empty
        String tsThread = ticket.containsKey("ts_thread") ? ticket.get("ts_thread").s() : "";

        // Put updated notes list back into DynamoDB
        putTicketWithNotes(ticketId,
            updatedNotes.stream()
                .map(av -> Map.of(
                    "noteId", av.m().get("noteId").s(),
                    "ts", av.m().get("ts").s()
                ))
                .collect(Collectors.toList()),
            tsThread
        );
    }

    /**
     * Update ts_thread for a ticket
     * 
     * @param ticketId
     * @param tsThread
     */
    public void updateThreadTs(String ticketId, String tsThread) {
    
    // Get existing ticket info
    Map<String, AttributeValue> existing = getTicket(ticketId);
    
    // If ticket does not exist, create it with empty notes and the provided tsThread
    java.util.List<AttributeValue> notes = existing != null && existing.containsKey("notes") ? existing.get("notes").l() : java.util.List.of();
    putTicketWithNotes(ticketId,
        notes.stream()
            .map(av -> Map.of(
                "noteId", av.m().get("noteId").s(), // store noteId as String
                "ts",     av.m().get("ts").s()
            ))
            .collect(java.util.stream.Collectors.toList()),
        tsThread
    );
  }

    /**
     * Try to create a ticket item only if it does not already exist. Returns true if created, false if item exists.
     * 
     * @param ticketId
     * @param tsThread
     * @return true if created, false if item exists
     */
    public boolean createTicketItem(String ticketId, String tsThread) {
        
        // Prepare Map item to put into DynamoDB
        try {
            Map<String, AttributeValue> item = new java.util.HashMap<>();
            item.put("tenantId", AttributeValue.builder().s(tenant.getTenantId()).build());
            item.put("ticketId", AttributeValue.builder().s(String.valueOf(ticketId)).build());
            item.put("ts_thread", AttributeValue.builder().s(tsThread).build());
            item.put("notes", AttributeValue.builder().l(java.util.List.of()).build());

            // Put item with condition that ticketId must not already exist
            dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(ticketId)") // Only put if ticketId does not exist
                .build());

            System.out.println("<" + java.time.LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES) +"> Ticket with ticketId " + item.get("ticketId").s() + " and ts_thread" + item.get("ts_thread") + " created in DynamoDB.");
            return true;
            
        // If condition fails (item with ticketId already exists), return false
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /**
     * Try to set ts_thread only if it is currently missing or empty. Returns true if updated, false if another value already present.
     * 
     * @param ticketId
     * @param tsThread
     * @return true if updated, false if another value already present
     */
    public boolean setThreadTs(String ticketId, String tsThread) {
        try {

            // update with condition that ts_thread is missing or empty
            UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("tenantId", AttributeValue.builder().s(String.valueOf(tenant.getTenantId())).build(),
                            "ticketId", AttributeValue.builder().s(String.valueOf(ticketId)).build()))
                .updateExpression("SET ts_thread = :ts")
                .conditionExpression("attribute_not_exists(ts_thread) OR ts_thread = :empty")
                .expressionAttributeValues(Map.of(
                    ":ts", AttributeValue.builder().s(tsThread).build(),
                    ":empty", AttributeValue.builder().s("").build()
                ))
                .build();

            dynamoDb.updateItem(req);
            return true;

         // If condition fails (ts_thread already set), return false
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    /**
     * Get ticketId by looking up the item with matching ts_thread. Returns null if not found.
     * 
     * @param threadTs
     * @return ticketId or null if not found
     */
    public String getTicketIdByThreadTs(String threadTs) {
        ScanRequest scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("tenantId = :tenant AND ts_thread = :ts")
            .expressionAttributeValues(Map.of(
                ":tenant", AttributeValue.builder().s(tenant.getTenantId()).build(),
                ":ts", AttributeValue.builder().s(threadTs).build()
            ))
            .build();

        ScanResponse response = dynamoDb.scan(scanRequest);
        if (!response.items().isEmpty()) {
            Map<String, AttributeValue> item = response.items().get(0);
            return item.get("ticketId").s();
        }
        return null;
    }
}
