package com.slackwise.slackwise.controller;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.slackwise.slackwise.model.Tenant;
import com.slackwise.slackwise.model.TenantConfig;
import com.slackwise.slackwise.model.Ticket;
import com.slackwise.slackwise.model.TimeEntry;
import com.slackwise.slackwise.model.RoutingRule;
import com.slackwise.slackwise.service.AmazonService;
import com.slackwise.slackwise.service.SlackService;
import com.slackwise.slackwise.service.ConnectwiseService;
import com.slackwise.slackwise.service.RoutingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slack.api.methods.SlackApiException;

@RestController
@RequestMapping("/api/connectwise")
public class ConnectwiseController {
    private static final Logger log = LoggerFactory.getLogger(ConnectwiseController.class);
    private static final int DEFAULT_AUTO_ASSIGNMENT_DELAY_MINUTES = 2;
    private static final Set<String> DEFAULT_ASSIGNMENT_EXCLUSION_KEYWORDS = Set.of(
        "compliance: set and review",
        "info systems audits",
        "internal system vulnerability",
        "monitor firewall and report",
        "routine security check",
        "documentation for review of permissions"
    );

    @Autowired
    ConnectwiseService connectwiseService;

    @Autowired
    SlackService slackService;

    @Autowired
    RoutingService routingService;

    @Autowired
    AmazonService amazonService;

    Tenant tenant;

    // Deprecated: now using database to track which tickets have been posted to Slack
    private Set<Integer> openTicketList = new HashSet<>();

    // add a simple lock object to serialize onNewEvent() calls
    private final Object eventLock = new Object();

    @Value("${user.id}")
    private int userId;
    
    @Value("${user.identifier}")
    private String userIdentifier;
    
    @Value("${lead.contact.name}")
    private String leadContactName;
    
    // Slack configuration properties
    @Value("${slack.bot.token}")
    private String slackBotToken;
    
    @Value("${slack.channel.id}")
    private String slackChannelId;

    @Value("${company.idnumber:19300}")
    private String fallbackTrackedCompanyId;

    private String tenantId;

    /**
     * Fetches all tickets for a given company ID.
     *
     * @param companyId The ID of the company.
     * @return List of all Open tickets from company.
     * @throws IOException          If an I/O error occurs.
     * @throws InterruptedException If the request is interrupted.
     */
    @GetMapping("/tickets/{companyId}")
    public List<Ticket> getOpenTicketsByCompanyId(@PathVariable String companyId) throws IOException, InterruptedException {

        List<Ticket> tickets = connectwiseService.fetchOpenTicketsByCompanyId(companyId);
        log.info("Fetched {} tickets for companyId={}", tickets.size(), companyId);

        for (Ticket t : tickets) {
            openTicketList.add(t.getId());
        }

        return tickets;
    }

    /**
     * Handles ConnectWise ticket events including ticket creation and updates.
     * 
     * @param recordId
     * @param payload
     * @return ResponseEntity with status message
     * @throws InterruptedException 
     * @throws IOException 
     * @throws SlackApiException 
     */
    @PostMapping("/events")
    public ResponseEntity<String> onNewEvent(@RequestParam("recordId") String recordId,@RequestBody Map<String, Object> payload) throws IOException, InterruptedException, SlackApiException {

        synchronized (eventLock) {
            
            log.info("Received ConnectWise action={} event for recordId={}", payload.get("Action"), recordId);

            ObjectMapper mapper = new ObjectMapper();

            /*
              
              Get tenantId from payload
             
              */

            if (!payload.containsKey("CompanyId")) {
                log.warn("No CompanyId found in payload for recordId={}", recordId);
                return ResponseEntity.badRequest().body("No CompanyId found in payload");
            }

            tenant = new Tenant(String.valueOf(payload.get("CompanyId"))); 
            tenantId = tenant.getTenantId();
            
            log.info("Extracted tenantId={}", tenantId);

            TenantConfig tenantConfig = amazonService.getTenantConfig(tenantId);
            Set<String> trackedCompanyIds = resolveTrackedCompanyIds(tenantConfig);
            int autoAssignmentDelayMinutes = resolveAutoAssignmentDelayMinutes(tenantConfig);
            Set<String> assignmentExclusionKeywords = resolveAssignmentExclusionKeywords(tenantConfig);
            String effectiveSlackBotToken = resolveSlackBotToken(tenantConfig);
            String effectiveDefaultChannelId = resolveDefaultSlackChannelId(tenantConfig);

            /*
                Get ticket ID from payload
            */ 
            Integer ticketId = -1;

            if (payload.containsKey("ID")) {
                ticketId = Integer.valueOf(String.valueOf(payload.get("ID")));
                log.info("Extracted ticketId={}", ticketId);
            } else  {
                log.warn("No ticket ID found in payload for recordId={}", recordId);
                return ResponseEntity.badRequest().body("No ticket ID found in payload");
            }

            /*
                Get entity and companyId from payload
            */
            String entityJson = "";
            String companyId = "";

            if (payload.containsKey("Entity")) {
                entityJson = String.valueOf(payload.get("Entity"));

                // Parse entity JSON string into a Map
                Map<String, Object> entity = null;
                if (entityJson != null && !"null".equals(entityJson)) {
                    entity = mapper.readValue(entityJson, new TypeReference<Map<String, Object>>() {});
                    log.debug("Extracted entity from payload");
                }

                if (entity == null) {
                    log.warn("Entity is null for recordId={} (likely deleted). Skipping processing", recordId);
                    return ResponseEntity.badRequest().body("No Entity found in payload");
                }

                // Extract companyId
                @SuppressWarnings("unchecked")
                Map<String, Object> company = (Map<String, Object>) entity.get("company");
                
                if (company != null && company.get("id") != null) {
                    companyId = String.valueOf(company.get("id"));
                    log.info("Extracted companyId={}", companyId);
                }

            } else {
                log.warn("No Entity found in payload (likely not a ticket): {}", payload);
                return ResponseEntity.badRequest().body("No Entity found in payload");
            }

            /*
                Process ticket from payload
            */
            
            // Only process if the companyId is in our tenant-config tracked list
            if (trackedCompanyIds.contains(companyId)) {
                Ticket ticket = connectwiseService.fetchTicketById(companyId, ticketId.toString());

                // If ticketFetch successful
                if (ticket != null) {

                    // Only process if action is "added" or "updated"
                    if (payload.get("Action").equals("added") || payload.get("Action").equals("updated")){

                        if (effectiveSlackBotToken == null || effectiveSlackBotToken.isBlank()) {
                            log.error("No Slack bot token configured for tenantId={}; skipping Slack sync for ticketId={}", tenantId, ticketId);
                            return ResponseEntity.ok("Missing Slack bot token configuration");
                        }

                        RoutingRule matchedRule = routingService.resolveRule(tenantId, ticket);
                        String resolvedChannelId = effectiveDefaultChannelId;
                        if (matchedRule != null
                            && matchedRule.getTargetChannelId() != null
                            && !matchedRule.getTargetChannelId().isBlank()) {
                            resolvedChannelId = matchedRule.getTargetChannelId();
                        }

                        log.info("Posting new Slack message for ticketId={} summary={}", ticketId, ticket.getSummary());
                        slackService.postNewTicket(tenantId, ticketId.toString(), ticket.getSummary(), resolvedChannelId);

                        log.info("Updating Slack thread for ticketId={}", ticketId);
                        slackService.updateTicketThread(tenantId, ticketId.toString(), ticket.getDiscussion(), ticket.getSummary(), resolvedChannelId);
                        
                        // Check if ticket is unassigned and assign to user if it is (with some exceptions for compliance/internal review tickets) after a delay to allow for any automatic assignment rules to run in ConnectWise first
                        final int finalTicketId = ticketId;
                        final String finalCompanyId = companyId;
                        final String ruleAssigneeIdentifier = matchedRule != null ? matchedRule.getTargetAssigneeIdentifier() : null;
                        final int finalAutoAssignmentDelayMinutes = autoAssignmentDelayMinutes;
                        final Set<String> finalAssignmentExclusionKeywords = assignmentExclusionKeywords;
                        if (payload.get("Action").equals("added")) {
                            new Thread(() -> {
                                try {
                                    
                                    if (finalAutoAssignmentDelayMinutes > 0) {
                                        Thread.sleep(finalAutoAssignmentDelayMinutes * 60000L);
                                    }
                                    Ticket ticket2 = connectwiseService.fetchTicketById(finalCompanyId, String.valueOf(finalTicketId));
                                    log.info("Re-fetched ticketId={} after delay to check assignment", finalTicketId);

                                    if (ticket2.getOwner() != null) {
                                        log.info("TicketId={} already assigned to {}. No assignment needed", finalTicketId, ticket2.getOwner().identifier);
                                    } else {
                                        
                                        log.info("TicketId={} is unassigned", finalTicketId);
                                        
                                        if (shouldSkipAutoAssignment(ticket2, finalAssignmentExclusionKeywords)) {
                                            log.info("TicketId={} appears compliance/internal review. Skipping assignment", finalTicketId);
                                            
                                        // Assign ticket to user and add time entry
                                        } else {
                                            TimeEntry timeEntry = new TimeEntry();

                                            timeEntry.setTicketId(finalTicketId);
                                            timeEntry.setDetailDescriptionFlag(false);
                                            timeEntry.setInternalAnalysisFlag(true);
                                            timeEntry.setResolutionFlag(false);
                                            timeEntry.setTimeStart(connectwiseService.getCurrentTimeForPayload());
                                            timeEntry.setActualHours(String.valueOf(0.0));
                                            timeEntry.setTimeEnd(null);
                                            timeEntry.setInfo(null);
                                            timeEntry.setEmailCcFlag(false);
                                            timeEntry.setEmailContactFlag(false);
                                            timeEntry.setEmailResourceFlag(false);

                                            String assignedIdentifier = userIdentifier;
                                            if (ruleAssigneeIdentifier != null && !ruleAssigneeIdentifier.isBlank()) {
                                                try {
                                                    connectwiseService.assignTicketToIdentifier(ruleAssigneeIdentifier, finalTicketId);
                                                    assignedIdentifier = ruleAssigneeIdentifier;
                                                } catch (Exception ex) {
                                                    log.warn("Rule assignee {} could not be applied for ticketId={}, falling back to default user", ruleAssigneeIdentifier, finalTicketId, ex);
                                                    connectwiseService.assignTicketTo(userId, userIdentifier, finalTicketId);
                                                }
                                            } else {
                                                connectwiseService.assignTicketTo(userId, userIdentifier, finalTicketId);
                                            }
                                            timeEntry.setNotes("Assigned / " + assignedIdentifier + " / ");
                                            connectwiseService.addTimeEntryToTicket(finalCompanyId, String.valueOf(finalTicketId), timeEntry);
                                        }
                                    }
                                } catch (Exception e) {
                                    log.error("Error in delayed assignment flow for ticketId={}", finalTicketId, e);
                                }
                                
                            }).start();
                        }

                        log.info("Finished processing event for ticketId={} summary={}", ticketId, ticket.getSummary());

                        return ResponseEntity.ok("Processed new ticket event for ticketId: " + ticketId);
                    } else {
                        log.info("Ignoring ConnectWise action={}", payload.get("Action"));
                        return ResponseEntity.ok("Ignored event action: " + payload.get("Action"));
                    }

                } else {
                    log.warn("Failed to fetch ticketId={}", ticketId);
                    return ResponseEntity.ok("Failed to fetch ticket " + ticketId);
                }
            } else {
                log.debug("Ignoring event from ticketId={} (companyId={} not tracked in {})", ticketId, companyId, trackedCompanyIds);
            }

            return ResponseEntity.ok("Received");
        }
    }
    
    private Set<String> resolveTrackedCompanyIds(TenantConfig config) {
        Set<String> configuredCompanyIds = parseDelimitedValues(config != null ? config.getTrackedCompanyIds() : null);
        if (!configuredCompanyIds.isEmpty()) {
            return configuredCompanyIds;
        }

        Set<String> fallback = new HashSet<>();
        if (fallbackTrackedCompanyId != null && !fallbackTrackedCompanyId.isBlank()) {
            fallback.add(fallbackTrackedCompanyId.trim());
        } else {
            fallback.add("19300");
        }
        return fallback;
    }

    private int resolveAutoAssignmentDelayMinutes(TenantConfig config) {
        Integer configuredDelay = config != null ? config.getAutoAssignmentDelayMinutes() : null;
        if (configuredDelay == null || configuredDelay < 0) {
            return DEFAULT_AUTO_ASSIGNMENT_DELAY_MINUTES;
        }
        return configuredDelay;
    }

    private Set<String> resolveAssignmentExclusionKeywords(TenantConfig config) {
        Set<String> configuredKeywords = parseDelimitedValues(config != null ? config.getAssignmentExclusionKeywords() : null)
            .stream()
            .map(String::toLowerCase)
            .collect(Collectors.toSet());

        if (!configuredKeywords.isEmpty()) {
            return configuredKeywords;
        }
        return DEFAULT_ASSIGNMENT_EXCLUSION_KEYWORDS;
    }

    private Set<String> parseDelimitedValues(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }

        Set<String> values = new HashSet<>();
        String[] split = raw.split("[,;\\r\\n]+");
        for (String entry : split) {
            if (entry != null && !entry.isBlank()) {
                values.add(entry.trim());
            }
        }
        return values;
    }

    private String resolveSlackBotToken(TenantConfig config) {
        if (config != null && config.getSlackBotToken() != null && !config.getSlackBotToken().isBlank()) {
            return config.getSlackBotToken().trim();
        }
        return slackBotToken != null ? slackBotToken.trim() : "";
    }

    private String resolveDefaultSlackChannelId(TenantConfig config) {
        if (config != null && config.getDefaultChannelId() != null && !config.getDefaultChannelId().isBlank()) {
            return config.getDefaultChannelId().trim();
        }
        return slackChannelId != null ? slackChannelId.trim() : "";
    }

    private boolean shouldSkipAutoAssignment(Ticket ticket, Set<String> exclusionKeywords) {
        String summary = ticket.getSummary() != null ? ticket.getSummary().toLowerCase() : "";
        boolean summaryMatched = exclusionKeywords.stream().anyMatch(summary::contains);
        if (summaryMatched) {
            return true;
        }

        String contactName = ticket.getContact() != null && ticket.getContact().getName() != null
            ? ticket.getContact().getName().toLowerCase()
            : "";
        String normalizedLeadContact = leadContactName != null ? leadContactName.toLowerCase() : "";
        return !normalizedLeadContact.isBlank() && contactName.contains(normalizedLeadContact);
    }
}
