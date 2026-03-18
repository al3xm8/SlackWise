package com.slackwise.slackwise.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.slack.api.Slack;
import com.slack.api.methods.SlackApiException;
import com.slack.api.methods.response.oauth.OAuthV2AccessResponse;
import com.slackwise.slackwise.model.TenantConfig;
import com.slackwise.slackwise.model.TenantSecrets;

/**
 * Manages Slack bot token refresh for token rotation-enabled apps.
 * 
 * When Slack token rotation is enabled, access tokens expire in 12 hours and must be 
 * refreshed using the refresh_token via oauth.v2.access with grant_type=refresh_token.
 * Both access_token and refresh_token rotate on each refresh.
 */
@Service
public class SlackTokenManager {
    private static final Logger log = LoggerFactory.getLogger(SlackTokenManager.class);
    
    // Refresh token buffer: refresh if token expires within this many seconds (default 5 min)
    private static final long TOKEN_REFRESH_BUFFER_SECONDS = 300L;
    
    @Autowired
    private AmazonService amazonService;

    @Autowired
    private TenantSecretsService tenantSecretsService;
    
    @Value("${slack.client.id:}")
    private String slackClientId;
    
    @Value("${slack.client.secret:}")
    private String slackClientSecret;
    
    private final Slack slack = Slack.getInstance();
    
    /**
     * Gets a valid Slack bot access token for the given tenant, refreshing if necessary.
     * 
     * This method checks if the stored token is about to expire and refreshes it if needed.
     * Both the access_token and refresh_token are updated in DynamoDB on successful refresh.
     * 
     * @param tenantId The tenant ID to get the token for
     * @return A valid bot access token, or null if no token is configured or refresh fails
     */
    public String getValidBotToken(String tenantId) {
        TenantSecrets secrets = tenantSecretsService.getSecrets(tenantId);
        if (!secrets.hasSlackAccess()) {
            TenantConfig legacyConfig = amazonService.getTenantConfig(tenantId);
            if (legacyConfig != null && legacyConfig.getSlackBotToken() != null && !legacyConfig.getSlackBotToken().isBlank()) {
                tenantSecretsService.upsertSlackSecrets(
                    tenantId,
                    legacyConfig.getSlackBotToken(),
                    legacyConfig.getSlackRefreshToken(),
                    legacyConfig.getSlackTokenExpiresAt()
                );
                secrets = tenantSecretsService.getSecrets(tenantId);
            }
        }

        if (!secrets.hasSlackAccess()) {
            log.debug("No Slack bot token configured for tenantId={}", tenantId);
            return null;
        }

        // If no expiration time is set, assume token doesn't need rotation (legacy token)
        if (secrets.getSlackTokenExpiresAt() == null) {
            log.debug("No expiration set for Slack token tenantId={}, assuming non-rotating token", tenantId);
            return secrets.getSlackBotToken();
        }

        // Check if token needs refresh
        long now = Instant.now().toEpochMilli();
        long expiresAt = secrets.getSlackTokenExpiresAt();
        long timeUntilExpiry = expiresAt - now;

        if (timeUntilExpiry > TOKEN_REFRESH_BUFFER_SECONDS * 1000) {
            // Token is still valid, no refresh needed
            log.debug("Slack token still valid for tenantId={}, expires in {} seconds", 
                tenantId, timeUntilExpiry / 1000);
            return secrets.getSlackBotToken();
        }

        // Token is expired or about to expire, refresh it
        log.info("Slack token expired or expiring soon for tenantId={}, attempting refresh", tenantId);
        return refreshToken(tenantId, secrets);
    }
    
    /**
     * Refreshes the Slack bot token using the refresh_token.
     * 
     * @param tenantId The tenant ID
     * @param config The current tenant config
     * @return The new valid access token, or null if refresh fails
     */
    private String refreshToken(String tenantId, TenantSecrets secrets) {
        String refreshToken = secrets.getSlackRefreshToken();
        
        if (refreshToken == null || refreshToken.isBlank()) {
            log.error("No refresh token available for tenantId={}, cannot refresh access token", tenantId);
            return null;
        }
        
        try {
            OAuthV2AccessResponse response = slack.methods().oauthV2Access(req -> req
                .clientId(slackClientId)
                .clientSecret(slackClientSecret)
                .grantType("refresh_token")
                .refreshToken(refreshToken)
            );
            
            if (response == null || !response.isOk()) {
                String error = response != null ? response.getError() : "unknown_error";
                log.error("Slack token refresh failed for tenantId={} error={}", tenantId, error);
                return null;
            }
            
            String newAccessToken = response.getAccessToken();
            String newRefreshToken = response.getRefreshToken();
            Integer expiresIn = response.getExpiresIn();
            Long newExpiresAt = expiresIn != null ? Instant.now().plusSeconds(expiresIn.longValue()).toEpochMilli() : null;
            
            if (newAccessToken == null || newAccessToken.isBlank()) {
                log.error("Slack token refresh returned empty access token for tenantId={}", tenantId);
                return null;
            }
            
            tenantSecretsService.upsertSlackSecrets(tenantId, newAccessToken, newRefreshToken, newExpiresAt);
            log.info("Slack token refresh successful for tenantId={}, new token expires in {} seconds", 
                tenantId, expiresIn);
            
            return newAccessToken;
            
        } catch (SlackApiException | java.io.IOException e) {
            log.error("Exception during Slack token refresh for tenantId={}", tenantId, e);
            return null;
        }
    }
}
