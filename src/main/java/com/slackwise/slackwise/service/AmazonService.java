package com.slackwise.slackwise.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.chat.ChatPostMessageResponse;
import com.slackwise.slackwise.model.RoutingRule;
import com.slackwise.slackwise.model.TenantConfig;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;
import software.amazon.awssdk.services.dynamodb.model.*;

@Service
public class AmazonService {
    private static final Logger log = LoggerFactory.getLogger(AmazonService.class);

    private static final String SK_CONFIG = "CONFIG";
    private static final String SK_PREFIX_RULE = "RULE#";
    private static final String SK_PREFIX_TICKET = "TICKET#";
    private static final String SK_PREFIX_WEBHOOK_EVENT = "WEBHOOK_EVENT#";
    // AWS configuration properties
    @Value("${aws.region}")
    private String awsRegion;

    // DynamoDB client
    private DynamoDbClient dynamoDb;

    @Value("${aws.accessKeyId:}")
    private String awsAccessKeyId;

    @Value("${aws.secretAccessKey:}")
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

    private static String webhookEventSk(String eventId) {
        return SK_PREFIX_WEBHOOK_EVENT + eventId;
    }

    // Initialize DynamoDB client
    @PostConstruct
    public void init() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
            .region(Region.of(awsRegion));

        if (!awsAccessKeyId.isBlank() && !awsSecretAccessKey.isBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                )
            );
            log.warn("DynamoDB client initialized using static AWS access keys from configuration.");
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("DynamoDB client initialized using AWS default credentials provider chain.");
        }

        dynamoDb = builder.build();
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
        String safeThreadTs = tsThread != null ? tsThread : "";
        
        // Prepare Map to put ticketId and thread ts and link them together
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("sk", AttributeValue.builder().s(ticketSk(ticketIdStr)).build());
        item.put("itemType", AttributeValue.builder().s("TICKET").build());
        item.put("ticketId", AttributeValue.builder().s(ticketIdStr).build());
        item.put("ts_thread", AttributeValue.builder().s(safeThreadTs).build());

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
        String safeThreadTs = tsThread != null ? tsThread : "";
        
        // Prepare Map item to put into DynamoDB
        try {
            Map<String, AttributeValue> item = new java.util.HashMap<>();
            item.put("tenantId", AttributeValue.builder().s(tenantId).build());
            item.put("sk", AttributeValue.builder().s(ticketSk(String.valueOf(ticketId))).build());
            item.put("itemType", AttributeValue.builder().s("TICKET").build());
            item.put("ticketId", AttributeValue.builder().s(String.valueOf(ticketId)).build());
            item.put("ts_thread", AttributeValue.builder().s(safeThreadTs).build());
            item.put("notes", AttributeValue.builder().l(java.util.List.of()).build());

            // Put item with condition that sk must not already exist
            dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(sk)") // Only put if sk does not exist
                .build());

            log.info("Created DynamoDB ticket item ticketId={} ts_thread={}", item.get("ticketId").s(), item.get("ts_thread").s());
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
        if (tenantId == null || tenantId.isBlank() || ticketId == null || ticketId.isBlank()) {
            log.warn("setThreadTs skipped because tenantId or ticketId is blank. tenantId={}, ticketId={}", tenantId, ticketId);
            return false;
        }
        if (tsThread == null || tsThread.isBlank()) {
            log.warn("setThreadTs skipped for tenantId={} ticketId={} because tsThread is blank", tenantId, ticketId);
            return false;
        }
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
        } catch (DynamoDbException e) {
            log.error("Failed to set thread_ts for tenantId={} ticketId={}", tenantId, ticketId, e);
            return false;
        }
    }
    /**
     * Track a Slack event ID to enforce idempotent webhook processing.
     *
     * @param tenantId Tenant identifier
     * @param eventId Slack event_id
     * @param ttlSeconds seconds from now for TTL cleanup
     * @return true if this event is new, false if already processed
     */
    public boolean registerSlackEventIfNew(String tenantId, String eventId, long ttlSeconds) {
        if (tenantId == null || tenantId.isBlank() || eventId == null || eventId.isBlank()) {
            return false;
        }

        long expiresAtEpoch = Instant.now().getEpochSecond() + Math.max(ttlSeconds, 300L);
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("tenantId", AttributeValue.builder().s(tenantId).build());
        item.put("sk", AttributeValue.builder().s(webhookEventSk(eventId)).build());
        item.put("itemType", AttributeValue.builder().s("WEBHOOK_EVENT").build());
        item.put("eventId", AttributeValue.builder().s(eventId).build());
        item.put("expiresAtEpoch", AttributeValue.builder().n(String.valueOf(expiresAtEpoch)).build());

        try {
            dynamoDb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .conditionExpression("attribute_not_exists(sk)")
                .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        } catch (DynamoDbException e) {
            log.error("Failed to register webhook event_id={} for tenantId={}", eventId, tenantId, e);
            return true;
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
        if (config.getAutoAssignmentDelayMinutes() != null) {
            item.put("autoAssignmentDelayMinutes", AttributeValue.builder().n(String.valueOf(config.getAutoAssignmentDelayMinutes())).build());
        }
        if (config.getAssignmentExclusionKeywords() != null) {
            item.put("assignmentExclusionKeywords", AttributeValue.builder().s(config.getAssignmentExclusionKeywords()).build());
        }
        if (config.getTrackedCompanyIds() != null) {
            item.put("trackedCompanyIds", AttributeValue.builder().s(config.getTrackedCompanyIds()).build());
        }
        if (config.getThemeMode() != null) {
            item.put("themeMode", AttributeValue.builder().s(config.getThemeMode()).build());
        }

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
        if (item.containsKey("autoAssignmentDelayMinutes")) {
            config.setAutoAssignmentDelayMinutes(Integer.valueOf(item.get("autoAssignmentDelayMinutes").n()));
        }
        if (item.containsKey("assignmentExclusionKeywords")) {
            config.setAssignmentExclusionKeywords(item.get("assignmentExclusionKeywords").s());
        }
        if (item.containsKey("trackedCompanyIds")) {
            config.setTrackedCompanyIds(item.get("trackedCompanyIds").s());
        }
        if (item.containsKey("themeMode")) {
            config.setThemeMode(item.get("themeMode").s());
        }
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
        if (rule.getTargetAssigneeIdentifier() != null) item.put("targetAssigneeIdentifier", AttributeValue.builder().s(rule.getTargetAssigneeIdentifier()).build());
        if (rule.getPrimaryField() != null) item.put("primaryField", AttributeValue.builder().s(rule.getPrimaryField()).build());
        if (rule.getPrimaryOperator() != null) item.put("primaryOperator", AttributeValue.builder().s(rule.getPrimaryOperator()).build());
        if (rule.getPrimaryValue() != null) item.put("primaryValue", AttributeValue.builder().s(rule.getPrimaryValue()).build());
        if (rule.getSecondaryField() != null) item.put("secondaryField", AttributeValue.builder().s(rule.getSecondaryField()).build());
        if (rule.getSecondaryOperator() != null) item.put("secondaryOperator", AttributeValue.builder().s(rule.getSecondaryOperator()).build());
        if (rule.getSecondaryValue() != null) item.put("secondaryValue", AttributeValue.builder().s(rule.getSecondaryValue()).build());
        if (rule.getJoinOperator() != null) item.put("joinOperator", AttributeValue.builder().s(rule.getJoinOperator()).build());
        if (rule.getConditions() != null && !rule.getConditions().isEmpty()) {
            List<AttributeValue> conditionValues = rule.getConditions().stream()
                .map(this::toConditionAttribute)
                .filter(conditionAttribute -> conditionAttribute != null)
                .toList();
            if (!conditionValues.isEmpty()) {
                item.put("conditions", AttributeValue.builder().l(conditionValues).build());
            }
            // Backward compatibility for any clients still reading primary/secondary fields.
            if (!item.containsKey("primaryField") && !item.containsKey("primaryOperator") && !item.containsKey("primaryValue")) {
                RoutingRule.RuleCondition first = rule.getConditions().get(0);
                if (first.getField() != null) item.put("primaryField", AttributeValue.builder().s(first.getField()).build());
                if (first.getOperator() != null) item.put("primaryOperator", AttributeValue.builder().s(first.getOperator()).build());
                if (first.getValue() != null) item.put("primaryValue", AttributeValue.builder().s(first.getValue()).build());
            }
            if (rule.getConditions().size() > 1
                && !item.containsKey("secondaryField")
                && !item.containsKey("secondaryOperator")
                && !item.containsKey("secondaryValue")) {
                RoutingRule.RuleCondition second = rule.getConditions().get(1);
                if (second.getField() != null) item.put("secondaryField", AttributeValue.builder().s(second.getField()).build());
                if (second.getOperator() != null) item.put("secondaryOperator", AttributeValue.builder().s(second.getOperator()).build());
                if (second.getValue() != null) item.put("secondaryValue", AttributeValue.builder().s(second.getValue()).build());
            }
        }

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
            if (item.containsKey("targetAssigneeIdentifier")) rule.setTargetAssigneeIdentifier(item.get("targetAssigneeIdentifier").s());
            if (item.containsKey("primaryField")) rule.setPrimaryField(item.get("primaryField").s());
            if (item.containsKey("primaryOperator")) rule.setPrimaryOperator(item.get("primaryOperator").s());
            if (item.containsKey("primaryValue")) rule.setPrimaryValue(item.get("primaryValue").s());
            if (item.containsKey("secondaryField")) rule.setSecondaryField(item.get("secondaryField").s());
            if (item.containsKey("secondaryOperator")) rule.setSecondaryOperator(item.get("secondaryOperator").s());
            if (item.containsKey("secondaryValue")) rule.setSecondaryValue(item.get("secondaryValue").s());
            if (item.containsKey("joinOperator")) rule.setJoinOperator(item.get("joinOperator").s());
            if (item.containsKey("conditions")) {
                List<RoutingRule.RuleCondition> conditions = item.get("conditions").l().stream()
                    .map(this::fromConditionAttribute)
                    .filter(condition -> condition != null)
                    .toList();
                if (!conditions.isEmpty()) {
                    rule.setConditions(conditions);
                }
            }
            if (rule.getConditions() == null || rule.getConditions().isEmpty()) {
                List<RoutingRule.RuleCondition> legacyConditions = buildConditionsFromLegacy(rule);
                if (!legacyConditions.isEmpty()) {
                    rule.setConditions(legacyConditions);
                }
            }
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

    private AttributeValue toConditionAttribute(RoutingRule.RuleCondition condition) {
        if (condition == null) {
            return null;
        }
        Map<String, AttributeValue> conditionMap = new java.util.HashMap<>();
        if (condition.getField() != null) {
            conditionMap.put("field", AttributeValue.builder().s(condition.getField()).build());
        }
        if (condition.getOperator() != null) {
            conditionMap.put("operator", AttributeValue.builder().s(condition.getOperator()).build());
        }
        if (condition.getValue() != null) {
            conditionMap.put("value", AttributeValue.builder().s(condition.getValue()).build());
        }
        if (conditionMap.isEmpty()) {
            return null;
        }
        return AttributeValue.builder().m(conditionMap).build();
    }

    private RoutingRule.RuleCondition fromConditionAttribute(AttributeValue attributeValue) {
        if (attributeValue == null || attributeValue.m() == null || attributeValue.m().isEmpty()) {
            return null;
        }
        Map<String, AttributeValue> conditionMap = attributeValue.m();
        RoutingRule.RuleCondition condition = new RoutingRule.RuleCondition();
        if (conditionMap.containsKey("field")) {
            condition.setField(conditionMap.get("field").s());
        }
        if (conditionMap.containsKey("operator")) {
            condition.setOperator(conditionMap.get("operator").s());
        }
        if (conditionMap.containsKey("value")) {
            condition.setValue(conditionMap.get("value").s());
        }
        if (!isPresent(condition.getField()) || !isPresent(condition.getOperator()) || !isPresent(condition.getValue())) {
            return null;
        }
        return condition;
    }

    private List<RoutingRule.RuleCondition> buildConditionsFromLegacy(RoutingRule rule) {
        List<RoutingRule.RuleCondition> conditions = new ArrayList<>();
        if (isPresent(rule.getPrimaryField()) && isPresent(rule.getPrimaryOperator()) && isPresent(rule.getPrimaryValue())) {
            RoutingRule.RuleCondition primary = new RoutingRule.RuleCondition();
            primary.setField(rule.getPrimaryField());
            primary.setOperator(rule.getPrimaryOperator());
            primary.setValue(rule.getPrimaryValue());
            conditions.add(primary);
        }
        if (isPresent(rule.getSecondaryField()) && isPresent(rule.getSecondaryOperator()) && isPresent(rule.getSecondaryValue())) {
            RoutingRule.RuleCondition secondary = new RoutingRule.RuleCondition();
            secondary.setField(rule.getSecondaryField());
            secondary.setOperator(rule.getSecondaryOperator());
            secondary.setValue(rule.getSecondaryValue());
            conditions.add(secondary);
        }
        return conditions;
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

}

