package com.slackwise.slackwise.controller;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.slackwise.slackwise.model.TenantConfig;
import com.slackwise.slackwise.service.AmazonService;
import com.slackwise.slackwise.service.ConnectwiseService;



@RestController
@RequestMapping("/api/slack")
public class SlackController {
    private static final Logger log = LoggerFactory.getLogger(SlackController.class);
    private static final long OAUTH_STATE_TTL_SECONDS = 600L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Autowired
    AmazonService amazonService;

    @Autowired
    ConnectwiseService connectwiseService;
    
    @Value("${company.id}")
    private String tenantId;

    @Value("${slack.client.id:}")
    private String slackClientId;

    @Value("${slack.client.secret:}")
    private String slackClientSecret;

    @Value("${slack.oauth.redirect.uri:}")
    private String slackOauthRedirectUri;

    @Value("${slack.oauth.bot.scopes:chat:write,channels:read,channels:history,groups:history,commands,users:read}")
    private String slackOauthBotScopes;

    @Value("${slack.oauth.user.scopes:}")
    private String slackOauthUserScopes;

    private final Map<String, OAuthState> oauthStateStore = new ConcurrentHashMap<>();
    private final Slack slack = Slack.getInstance();

    private static class OAuthState {
        private final String tenantId;
        private final Instant expiresAt;

        private OAuthState(String tenantId, Instant expiresAt) {
            this.tenantId = tenantId;
            this.expiresAt = expiresAt;
        }
    }

    @GetMapping("/oauth/install")
    public ResponseEntity<Void> startOAuthInstall(@RequestParam(value = "tenantId", required = false) String requestedTenantId) {
        validateOAuthConfig();

        String effectiveTenantId = resolveEffectiveTenantId(requestedTenantId);
        String state = generateState();
        oauthStateStore.put(state, new OAuthState(effectiveTenantId, Instant.now().plusSeconds(OAUTH_STATE_TTL_SECONDS)));

        String installUrl = buildOAuthAuthorizeUrl(state);
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, installUrl)
            .build();
    }

    @GetMapping("/oauth/callback")
    public ResponseEntity<Map<String, String>> handleOAuthCallback(
        @RequestParam(value = "code", required = false) String code,
        @RequestParam(value = "state", required = false) String state,
        @RequestParam(value = "error", required = false) String error
    ) throws IOException, SlackApiException {
        validateOAuthConfig();

        if (error != null && !error.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack OAuth denied: " + error);
        }

        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Slack OAuth code");
        }
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Slack OAuth state");
        }

        OAuthState savedState = oauthStateStore.remove(state);
        if (savedState == null || savedState.expiresAt.isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired Slack OAuth state");
        }

        OAuthV2AccessResponse oauthResponse = slack.methods().oauthV2Access(req -> req
            .clientId(slackClientId)
            .clientSecret(slackClientSecret)
            .code(code)
            .redirectUri(slackOauthRedirectUri)
        );

        if (oauthResponse == null || !oauthResponse.isOk()) {
            String slackError = oauthResponse != null ? oauthResponse.getError() : "unknown_error";
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack OAuth exchange failed: " + slackError);
        }

        String botToken = oauthResponse.getAccessToken();
        if (botToken == null || botToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack OAuth did not return a bot token");
        }

        String teamId = oauthResponse.getTeam() != null ? oauthResponse.getTeam().getId() : null;

        TenantConfig config = amazonService.getTenantConfig(savedState.tenantId);
        if (config == null) {
            config = new TenantConfig();
        }

        config.setTenantId(savedState.tenantId);
        config.setSlackBotToken(botToken);
        if (teamId != null && !teamId.isBlank()) {
            config.setSlackTeamId(teamId);
        }

        amazonService.putTenantConfig(savedState.tenantId, config);

        Map<String, String> body = new HashMap<>();
        body.put("message", "Slack workspace connected");
        body.put("tenantId", savedState.tenantId);
        body.put("teamId", teamId != null ? teamId : "");

        return ResponseEntity.ok(body);
    }

    private String resolveEffectiveTenantId(String requestedTenantId) {
        String candidate = requestedTenantId != null ? requestedTenantId.trim() : "";
        if (!candidate.isBlank()) {
            return candidate;
        }

        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant ID is required");
        }
        return tenantId;
    }

    private void validateOAuthConfig() {
        if (slackClientId == null || slackClientId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing SLACK_CLIENT_ID configuration");
        }
        if (slackClientSecret == null || slackClientSecret.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing SLACK_CLIENT_SECRET configuration");
        }
        if (slackOauthRedirectUri == null || slackOauthRedirectUri.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing SLACK_OAUTH_REDIRECT_URI configuration");
        }
    }

    private String buildOAuthAuthorizeUrl(String state) {
        StringBuilder url = new StringBuilder("https://slack.com/oauth/v2/authorize");
        url.append("?client_id=").append(urlEncode(slackClientId));
        url.append("&scope=").append(urlEncode(slackOauthBotScopes));
        url.append("&redirect_uri=").append(urlEncode(slackOauthRedirectUri));
        url.append("&state=").append(urlEncode(state));

        if (slackOauthUserScopes != null && !slackOauthUserScopes.isBlank()) {
            url.append("&user_scope=").append(urlEncode(slackOauthUserScopes));
        }

        return url.toString();
    }

    private String generateState() {
        byte[] random = new byte[24];
        SECURE_RANDOM.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

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
