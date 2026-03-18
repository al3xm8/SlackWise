package com.slackwise.slackwise.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.slackwise.slackwise.model.TenantSecrets;

import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.PutSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

@Service
public class TenantSecretsService {
    private static final Logger log = LoggerFactory.getLogger(TenantSecretsService.class);

    @Value("${aws.region}")
    private String awsRegion;

    @Value("${aws.accessKeyId:}")
    private String awsAccessKeyId;

    @Value("${aws.secretAccessKey:}")
    private String awsSecretAccessKey;

    @Value("${aws.secrets.prefix:dropwise/tenants}")
    private String secretsPrefix;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private SecretsManagerClient secretsManager;

    @PostConstruct
    public void init() {
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
            .region(Region.of(awsRegion));

        if (!awsAccessKeyId.isBlank() && !awsSecretAccessKey.isBlank()) {
            builder.credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey)
                )
            );
            log.warn("Secrets Manager client initialized using static AWS access keys from configuration.");
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
            log.info("Secrets Manager client initialized using AWS default credentials provider chain.");
        }

        secretsManager = builder.build();
    }

    public TenantSecrets getSecrets(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return new TenantSecrets();
        }

        try {
            String secretString = secretsManager.getSecretValue(GetSecretValueRequest.builder()
                .secretId(secretName(tenantId))
                .build())
                .secretString();

            if (secretString == null || secretString.isBlank()) {
                return new TenantSecrets();
            }

            return objectMapper.readValue(secretString, TenantSecrets.class);
        } catch (ResourceNotFoundException notFound) {
            return new TenantSecrets();
        } catch (JsonProcessingException parseError) {
            log.error("Failed to parse tenant secrets payload for tenantId={}", tenantId, parseError);
            return new TenantSecrets();
        } catch (SecretsManagerException serviceError) {
            log.error("Failed to load tenant secrets for tenantId={}", tenantId, serviceError);
            return new TenantSecrets();
        }
    }

    public boolean hasAnySecrets(String tenantId) {
        return getSecrets(tenantId).hasAnySecrets();
    }

    public boolean hasSlackSecrets(String tenantId) {
        return getSecrets(tenantId).hasSlackAccess();
    }

    public boolean hasConnectwiseSecrets(String tenantId) {
        return getSecrets(tenantId).hasConnectwiseCredentials();
    }

    public void upsertSlackSecrets(String tenantId, String botToken, String refreshToken, Long expiresAt) {
        TenantSecrets secrets = getSecrets(tenantId);
        if (isPresent(botToken)) {
            secrets.setSlackBotToken(botToken.trim());
        }
        if (isPresent(refreshToken)) {
            secrets.setSlackRefreshToken(refreshToken.trim());
        }
        if (expiresAt != null) {
            secrets.setSlackTokenExpiresAt(expiresAt);
        }
        saveSecrets(tenantId, secrets);
    }

    public void upsertConnectwiseSecrets(String tenantId, String clientId, String publicKey, String privateKey) {
        TenantSecrets secrets = getSecrets(tenantId);
        if (isPresent(clientId)) {
            secrets.setConnectwiseClientId(clientId.trim());
        }
        if (isPresent(publicKey)) {
            secrets.setConnectwisePublicKey(publicKey.trim());
        }
        if (isPresent(privateKey)) {
            secrets.setConnectwisePrivateKey(privateKey.trim());
        }
        saveSecrets(tenantId, secrets);
    }

    private void saveSecrets(String tenantId, TenantSecrets secrets) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is null/blank");
        }

        String secretId = secretName(tenantId);
        String payload = writeSecretsPayload(secrets);

        try {
            secretsManager.putSecretValue(PutSecretValueRequest.builder()
                .secretId(secretId)
                .secretString(payload)
                .build());
        } catch (ResourceNotFoundException notFound) {
            secretsManager.createSecret(CreateSecretRequest.builder()
                .name(secretId)
                .secretString(payload)
                .build());
        }
    }

    private String writeSecretsPayload(TenantSecrets secrets) {
        try {
            return objectMapper.writeValueAsString(secrets);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize tenant secrets payload", e);
        }
    }

    private String secretName(String tenantId) {
        String normalizedPrefix = secretsPrefix != null ? secretsPrefix.trim() : "dropwise/tenants";
        String normalizedTenantId = tenantId.trim();
        return normalizedPrefix + "/" + normalizedTenantId + "/integrations";
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
