package org.suffleport.zwloader.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class HaIntegrationAuthService {

    private static final String LEGACY_COMPAT_TOKEN = "ha_local_token";

    private final String webhookToken;
    private final String homeAssistantToken;

    public HaIntegrationAuthService(@Value("${app.ha.webhook-token:ha_local_token}") String webhookToken,
                                    @Value("${api.home_assistant:}") String homeAssistantToken) {
        this.webhookToken = webhookToken;
        this.homeAssistantToken = homeAssistantToken;
    }

    public void requireValidToken(String token) {
        boolean hasWebhookToken = webhookToken != null && !webhookToken.isBlank();
        boolean hasLongLivedToken = homeAssistantToken != null && !homeAssistantToken.isBlank();
        if (!hasWebhookToken && !hasLongLivedToken) {
            return;
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Токен интеграции Home Assistant обязателен");
        }
        boolean matchesWebhook = hasWebhookToken && MessageDigest.isEqual(
                webhookToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
        boolean matchesLongLived = hasLongLivedToken && MessageDigest.isEqual(
                homeAssistantToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8)
        );
        boolean matchesLegacy = MessageDigest.isEqual(
            LEGACY_COMPAT_TOKEN.getBytes(StandardCharsets.UTF_8),
            token.getBytes(StandardCharsets.UTF_8)
        );
        boolean matches = matchesWebhook || matchesLongLived || matchesLegacy;
        if (!matches) {
            throw new IllegalArgumentException("Неверный токен интеграции Home Assistant");
        }
    }
}