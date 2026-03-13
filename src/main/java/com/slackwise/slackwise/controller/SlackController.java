package com.slackwise.slackwise.controller;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final long SLACK_REQUEST_MAX_AGE_SECONDS = 300L;
    private static final long SLACK_EVENT_IDEMPOTENCY_TTL_SECONDS = 86400L;
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

    @Value("${slack.signing.secret:}")
    private String slackSigningSecret;

    private final Map<String, OAuthState> oauthStateStore = new ConcurrentHashMap<>();
    private final Slack slack = Slack.getInstance();
    private final ObjectMapper objectMapper = new ObjectMapper();

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
        cleanupExpiredOAuthStates();
        String state = generateState();
        Instant expiresAt = Instant.now().plusSeconds(OAUTH_STATE_TTL_SECONDS);
        oauthStateStore.put(state, new OAuthState(effectiveTenantId, expiresAt));
        log.info("Starting Slack OAuth install for tenantId={} state={} expiresAt={}",
            effectiveTenantId, abbreviateState(state), expiresAt);

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
        log.info("Slack OAuth callback received: codePresent={} statePresent={} errorPresent={}",
            code != null && !code.isBlank(),
            state != null && !state.isBlank(),
            error != null && !error.isBlank());

        if (error != null && !error.isBlank()) {
            log.warn("Slack OAuth denied by user: {}", error);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack OAuth denied: " + error);
        }

        if (code == null || code.isBlank()) {
            log.warn("Slack OAuth callback missing code");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Slack OAuth code");
        }
        if (state == null || state.isBlank()) {
            log.warn("Slack OAuth callback missing state");
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Slack OAuth state");
        }

        OAuthState savedState = oauthStateStore.remove(state);
        if (savedState == null || savedState.expiresAt.isBefore(Instant.now())) {
            log.warn("Invalid or expired Slack OAuth state received: {}", abbreviateState(state));
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
            log.warn("Slack OAuth exchange failed for tenantId={} error={}", savedState.tenantId, slackError);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack OAuth exchange failed: " + slackError);
        }

        String botToken = oauthResponse.getAccessToken();
        if (botToken == null || botToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Slack OAuth did not return a bot token");
        }

        String refreshToken = oauthResponse.getRefreshToken();
        Integer expiresIn = oauthResponse.getExpiresIn();
        Long expiresAt = expiresIn != null ? Instant.now().plusSeconds(expiresIn.longValue()).toEpochMilli() : null;

        String teamId = oauthResponse.getTeam() != null ? oauthResponse.getTeam().getId() : null;

        TenantConfig config = amazonService.getTenantConfig(savedState.tenantId);
        if (config == null) {
            config = new TenantConfig();
        }

        config.setTenantId(savedState.tenantId);
        config.setSlackBotToken(botToken);
        config.setSlackRefreshToken(refreshToken);
        config.setSlackTokenExpiresAt(expiresAt);
        if (teamId != null && !teamId.isBlank()) {
            config.setSlackTeamId(teamId);
        }

        amazonService.putTenantConfig(savedState.tenantId, config);
        log.info("Slack OAuth install completed for tenantId={} teamId={} tokenRotationEnabled={}", 
            savedState.tenantId, teamId, refreshToken != null);

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

    private void cleanupExpiredOAuthStates() {
        Instant now = Instant.now();
        oauthStateStore.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }

    private String abbreviateState(String state) {
        if (state == null || state.isBlank()) {
            return "null";
        }
        return state.length() <= 8 ? state : state.substring(0, 8) + "...";
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolveEventTenantId() {
        if (tenantId == null || tenantId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Missing COMPANY_ID configuration");
        }
        return tenantId;
    }

    private boolean isSlackRequestAuthentic(String timestampHeader, String signatureHeader, String rawBody) {
        if (slackSigningSecret == null || slackSigningSecret.isBlank()) {
            log.error("Missing SLACK_SIGNING_SECRET configuration");
            return false;
        }
        if (timestampHeader == null || timestampHeader.isBlank() || signatureHeader == null || signatureHeader.isBlank() || rawBody == null) {
            return false;
        }

        long requestTimestamp;
        try {
            requestTimestamp = Long.parseLong(timestampHeader);
        } catch (NumberFormatException ex) {
            log.warn("Slack timestamp header is not numeric: {}", timestampHeader);
            return false;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - requestTimestamp) > SLACK_REQUEST_MAX_AGE_SECONDS) {
            log.warn("Rejected Slack request outside replay window. requestTs={} now={}", requestTimestamp, now);
            return false;
        }

        String baseString = "v0:" + timestampHeader + ":" + rawBody;
        String expectedSignature = "v0=" + hmacSha256Hex(baseString, slackSigningSecret);
        return MessageDigest.isEqual(
            expectedSignature.getBytes(StandardCharsets.UTF_8),
            signatureHeader.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute Slack signature", ex);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Handles incoming Slack events, including URL verification challenges.
     *
     * @param rawBody The incoming request payload from Slack.
     * @return A ResponseEntity containing the challenge response or a simple "OK" message.
     * @throws IOException
     * @throws SlackApiException
     */
    @PostMapping("/events")
    public ResponseEntity<String> handleSlackEvents(
        @RequestHeader(value = "X-Slack-Signature", required = false) String slackSignature,
        @RequestHeader(value = "X-Slack-Request-Timestamp", required = false) String slackTimestamp,
        @RequestBody String rawBody
    ) throws IOException, SlackApiException {

        if (!isSlackRequestAuthentic(slackTimestamp, slackSignature, rawBody)) {
            log.warn("Rejected Slack event: signature verification failed");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Slack signature");
        }

        Map<String, Object> payload;
        try {
            payload = objectMapper.readValue(rawBody, new TypeReference<Map<String, Object>>() {});
        } catch (IOException ex) {
            log.warn("Invalid Slack event payload", ex);
            return ResponseEntity.badRequest().body("Invalid JSON payload");
        }

        log.info("Received Slack event");

        if ("url_verification".equals(payload.get("type"))) {
            log.info("Slack challenge received");
            return ResponseEntity.ok((String) payload.get("challenge"));
        }

        String effectiveTenantId = resolveEventTenantId();
        String eventId = payload.get("event_id") != null ? String.valueOf(payload.get("event_id")) : null;
        if (eventId != null && !eventId.isBlank()) {
            boolean isNewEvent = amazonService.registerSlackEventIfNew(
                effectiveTenantId,
                eventId,
                SLACK_EVENT_IDEMPOTENCY_TTL_SECONDS
            );
            if (!isNewEvent) {
                log.info("Ignoring duplicate Slack event_id={}", eventId);
                return ResponseEntity.ok("Duplicate event ignored");
            }
        }

        if (payload.containsKey("event")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> event = (Map<String, Object>) payload.get("event");
            String eventType = event != null ? (String) event.get("type") : null;

            if (event != null && (event.get("subtype") != null && !event.get("subtype").equals("file_share"))) {
                log.debug("Ignoring Slack event with subtype={}", event.get("subtype"));
                return ResponseEntity.ok("Ignored subtype");
            }
            if (event != null && event.containsKey("bot_id")) {
                log.debug("Ignoring bot event");
                return ResponseEntity.ok("Ignored bot event");
            }

            if (event != null && "message".equals(eventType)) {
                String text = event.get("text") != null ? String.valueOf(event.get("text")) : null;
                String user = event.get("user") != null ? String.valueOf(event.get("user")) : null;
                String threadTs = event.get("thread_ts") != null ? String.valueOf(event.get("thread_ts")) : null;
                String ts = event.get("ts") != null ? String.valueOf(event.get("ts")) : null;

                String ticketId = amazonService.getTicketIdByThreadTs(effectiveTenantId, threadTs != null ? threadTs : ts);
                if (text != null && text.startsWith("🆔")) {
                    log.debug("Ignoring app-generated Slack message: {}", text);
                    return ResponseEntity.ok("Ignored app-generated message");
                }
                if (ticketId != null) {
                    log.info("Message in thread for ticketId={} from user={}", ticketId, user);

                    new Thread(() -> {
                        try {
                            connectwiseService.addSlackReplyToTicket(effectiveTenantId, ticketId, text, event);
                        } catch (Exception e) {
                            log.error("Failed to process Slack reply for ticketId={}", ticketId, e);
                        }
                    }).start();
                } else {
                    log.warn("No ticket found for thread_ts={} or ts={}", threadTs, ts);
                }
                return ResponseEntity.ok("OK");
            }
        }

        return ResponseEntity.ok("OK");
    }
}

