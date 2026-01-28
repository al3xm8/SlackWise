package com.slackwise.slackwise.service;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slackwise.slackwise.model.RoutingRule;
import com.slackwise.slackwise.model.TenantConfig;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Service
public class AmazonService {

    private static final String SK_CONFIG = "CONFIG";
    private static final String SK_PREFIX_RULE = "RULE#";
    private static final String SK_PREFIX_TICKET = "TICKET#";

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
    
    // Slack configuration properties
    @Value("${slack.bot.token}")
    private String slackBotToken;

    @Value("${slack.channel.id}")
    private String slackChannelId;
    
    private final Slack slack = Slack.getInstance();

    private static String ticketSk(String ticketId) {
        return SK_PREFIX_TICKET + ticketId;
    }

    private static String ruleSk(int priority, String ruleId) {
        return String.format("RULE#%04d#%s", priority, ruleId);
    }

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
    public void putTicketWithNotes(String tenantId, String ticketId, List<Map<String, String>> notes, String tsThread) {
        // Validate input
        if (ticketId == null || ticketId.isBlank()) throw new IllegalArgumentException("ticketId is null/blank");
        String ticketIdStr = String.valueOf(ticketId);
        
        // Prepare Map to put ticketId and thread ts and link them together
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("sk", AttributeValue.builder().s(ticketSk(ticketIdStr)).build());
        item.put("itemType", AttributeValue.builder().s("TICKET").build());
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
    public Map<String, AttributeValue> getTicket(String tenantId, String ticketId) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("tenantId", AttributeValue.builder().s(tenantId).build(),
                        "sk", AttributeValue.builder().s(ticketSk(ticketId)).build()))
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
    public void addNoteToTicket(String tenantId, String ticketId, String noteId, String ts) 
    {
        // Get existing ticket info from DynamoDB
        Map<String, AttributeValue> ticket = getTicket(tenantId, ticketId);

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
        putTicketWithNotes(tenantId, ticketId,
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
    public void updateThreadTs(String tenantId, String ticketId, String tsThread) {
    
    // Get existing ticket info
    Map<String, AttributeValue> existing = getTicket(tenantId, ticketId);
    
    // If ticket does not exist, create it with empty notes and the provided tsThread
    java.util.List<AttributeValue> notes = existing != null && existing.containsKey("notes") ? existing.get("notes").l() : java.util.List.of();
    putTicketWithNotes(tenantId, ticketId,
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
    public boolean createTicketItem(String tenantId, String ticketId, String tsThread) {
        
        // Prepare Map item to put into DynamoDB
        try {
            Map<String, AttributeValue> item = new java.util.HashMap<>();
            item.put("tenantId", AttributeValue.builder().s(tenantId).build());
            item.put("sk", AttributeValue.builder().s(ticketSk(String.valueOf(ticketId))).build());
            item.put("itemType", AttributeValue.builder().s("TICKET").build());
            item.put("ticketId", AttributeValue.builder().s(String.valueOf(ticketId)).build());
            item.put("ts_thread", AttributeValue.builder().s(tsThread).build());
            item.put("notes", AttributeValue.builder().l(java.util.List.of()).build());

            // Put item with condition that sk must not already exist
            dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(sk)") // Only put if sk does not exist
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
    public boolean setThreadTs(String tenantId, String ticketId, String tsThread) {
        try {

            // update with condition that ts_thread is missing or empty
            UpdateItemRequest req = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("tenantId", AttributeValue.builder().s(tenantId).build(),
                            "sk", AttributeValue.builder().s(ticketSk(ticketId)).build()))
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
     * Get ticketId by looking up the item with matching ts_thread. Returns null if ticketId is not found or tenant is not set.
     * 
     * @param threadTs
     * @return ticketId or null if not found
     * @throws SlackApiException 
     * @throws IOException 
     */
    public String getTicketIdByThreadTs(String tenantId, String threadTs) throws IOException, SlackApiException {
        
        if (tenantId == null) {
            
            ChatPostMessageResponse response = slack.methods(slackBotToken).chatPostMessage(req -> req
                .channel(slackChannelId)
                .text("ERR0R: Tenant not set when trying to getTicketIdByThreadTs for threadTs: " + threadTs)
                .mrkdwn(true)
            );
                   
            return null;
        }
        
        ScanRequest scanRequest = ScanRequest.builder()
            .tableName(tableName)
            .filterExpression("tenantId = :tenant AND begins_with(sk, :ticketPrefix) AND ts_thread = :ts")
            .expressionAttributeValues(Map.of(
                ":tenant", AttributeValue.builder().s(tenantId).build(),
                ":ticketPrefix", AttributeValue.builder().s(SK_PREFIX_TICKET).build(),
                ":ts", AttributeValue.builder().s(threadTs).build()
            ))
            .build();

        ScanResponse response = dynamoDb.scan(scanRequest);
        if (!response.items().isEmpty()) {
            Map<String, AttributeValue> item = response.items().get(0);
            if (item.containsKey("ticketId")) {
                return item.get("ticketId").s();
            }
            if (item.containsKey("sk")) {
                String sk = item.get("sk").s();
                if (sk != null && sk.startsWith(SK_PREFIX_TICKET)) {
                    return sk.substring(SK_PREFIX_TICKET.length());
                }
            }
        }
        return null;
    }

    public void putTenantConfig(String tenantId, TenantConfig config) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is null/blank");
        if (config == null) throw new IllegalArgumentException("config is null");

        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("sk", AttributeValue.builder().s(SK_CONFIG).build());
        item.put("itemType", AttributeValue.builder().s("CONFIG").build());

        if (config.getSlackTeamId() != null) item.put("slackTeamId", AttributeValue.builder().s(config.getSlackTeamId()).build());
        if (config.getSlackBotToken() != null) item.put("slackBotToken", AttributeValue.builder().s(config.getSlackBotToken()).build());
        if (config.getDefaultChannelId() != null) item.put("defaultChannelId", AttributeValue.builder().s(config.getDefaultChannelId()).build());
        if (config.getConnectwiseSite() != null) item.put("connectwiseSite", AttributeValue.builder().s(config.getConnectwiseSite()).build());
        if (config.getConnectwiseClientId() != null) item.put("connectwiseClientId", AttributeValue.builder().s(config.getConnectwiseClientId()).build());
        if (config.getConnectwisePublicKey() != null) item.put("connectwisePublicKey", AttributeValue.builder().s(config.getConnectwisePublicKey()).build());
        if (config.getConnectwisePrivateKey() != null) item.put("connectwisePrivateKey", AttributeValue.builder().s(config.getConnectwisePrivateKey()).build());
        if (config.getDisplayName() != null) item.put("displayName", AttributeValue.builder().s(config.getDisplayName()).build());

        dynamoDb.putItem(PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build());
    }

    public TenantConfig getTenantConfig(String tenantId) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("tenantId", AttributeValue.builder().s(tenantId).build(),
                        "sk", AttributeValue.builder().s(SK_CONFIG).build()))
            .build());

        Map<String, AttributeValue> item = response.item();
        if (item == null || item.isEmpty()) return null;

        TenantConfig config = new TenantConfig();
        config.setTenantId(tenantId);
        if (item.containsKey("slackTeamId")) config.setSlackTeamId(item.get("slackTeamId").s());
        if (item.containsKey("slackBotToken")) config.setSlackBotToken(item.get("slackBotToken").s());
        if (item.containsKey("defaultChannelId")) config.setDefaultChannelId(item.get("defaultChannelId").s());
        if (item.containsKey("connectwiseSite")) config.setConnectwiseSite(item.get("connectwiseSite").s());
        if (item.containsKey("connectwiseClientId")) config.setConnectwiseClientId(item.get("connectwiseClientId").s());
        if (item.containsKey("connectwisePublicKey")) config.setConnectwisePublicKey(item.get("connectwisePublicKey").s());
        if (item.containsKey("connectwisePrivateKey")) config.setConnectwisePrivateKey(item.get("connectwisePrivateKey").s());
        if (item.containsKey("displayName")) config.setDisplayName(item.get("displayName").s());
        return config;
    }

    public RoutingRule putRoutingRule(String tenantId, RoutingRule rule) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is null/blank");
        if (rule == null) throw new IllegalArgumentException("rule is null");

        if (rule.getRuleId() == null || rule.getRuleId().isBlank()) {
            rule.setRuleId(UUID.randomUUID().toString());
        }
        rule.setTenantId(tenantId);

        String sk = ruleSk(rule.getPriority(), rule.getRuleId());
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("sk", AttributeValue.builder().s(sk).build());
        item.put("itemType", AttributeValue.builder().s("RULE").build());
        item.put("ruleId", AttributeValue.builder().s(rule.getRuleId()).build());
        item.put("priority", AttributeValue.builder().n(String.valueOf(rule.getPriority())).build());
        item.put("enabled", AttributeValue.builder().bool(rule.isEnabled()).build());
        if (rule.getMatchContact() != null) item.put("matchContact", AttributeValue.builder().s(rule.getMatchContact()).build());
        if (rule.getMatchSubject() != null) item.put("matchSubject", AttributeValue.builder().s(rule.getMatchSubject()).build());
        if (rule.getMatchSubjectRegex() != null) item.put("matchSubjectRegex", AttributeValue.builder().s(rule.getMatchSubjectRegex()).build());
        if (rule.getTargetChannelId() != null) item.put("targetChannelId", AttributeValue.builder().s(rule.getTargetChannelId()).build());

        dynamoDb.putItem(PutItemRequest.builder()
            .tableName(tableName)
            .item(item)
            .build());

        return rule;
    }

    public List<RoutingRule> getRoutingRules(String tenantId) {
        QueryRequest req = QueryRequest.builder()
            .tableName(tableName)
            .keyConditionExpression("tenantId = :tenant AND begins_with(sk, :rulePrefix)")
            .expressionAttributeValues(Map.of(
                ":tenant", AttributeValue.builder().s(tenantId).build(),
                ":rulePrefix", AttributeValue.builder().s(SK_PREFIX_RULE).build()
            ))
            .build();

        QueryResponse response = dynamoDb.query(req);
        List<RoutingRule> rules = new java.util.ArrayList<>();
        for (Map<String, AttributeValue> item : response.items()) {
            RoutingRule rule = new RoutingRule();
            rule.setTenantId(tenantId);
            if (item.containsKey("ruleId")) rule.setRuleId(item.get("ruleId").s());
            if (item.containsKey("priority")) rule.setPriority(Integer.parseInt(item.get("priority").n()));
            if (item.containsKey("enabled")) rule.setEnabled(item.get("enabled").bool());
            if (item.containsKey("matchContact")) rule.setMatchContact(item.get("matchContact").s());
            if (item.containsKey("matchSubject")) rule.setMatchSubject(item.get("matchSubject").s());
            if (item.containsKey("matchSubjectRegex")) rule.setMatchSubjectRegex(item.get("matchSubjectRegex").s());
            if (item.containsKey("targetChannelId")) rule.setTargetChannelId(item.get("targetChannelId").s());
            rules.add(rule);
        }
        return rules;
    }

    public void deleteRoutingRule(String tenantId, int priority, String ruleId) {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("tenantId is null/blank");
        if (ruleId == null || ruleId.isBlank()) throw new IllegalArgumentException("ruleId is null/blank");

        dynamoDb.deleteItem(DeleteItemRequest.builder()
            .tableName(tableName)
            .key(Map.of("tenantId", AttributeValue.builder().s(tenantId).build(),
                        "sk", AttributeValue.builder().s(ruleSk(priority, ruleId)).build()))
            .build());
    }
}
