package org.suffleport.zwloader.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class HomeAssistantIntegrationService {

    private final String homeAssistantUrl;
    private final String homeAssistantToken;

    public HomeAssistantIntegrationService(@Value("${api.home_assistant.url:}") String homeAssistantUrl,
                                           @Value("${api.home_assistant:}") String homeAssistantToken) {
        this.homeAssistantUrl = homeAssistantUrl == null ? "" : homeAssistantUrl.trim();
        this.homeAssistantToken = homeAssistantToken == null ? "" : homeAssistantToken.trim();
    }

    public boolean isConfigured() {
        return !homeAssistantUrl.isBlank() && !homeAssistantToken.isBlank();
    }

    public Map<String, Object> ping() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("configured", isConfigured());
        out.put("url", homeAssistantUrl);

        if (!isConfigured()) {
            out.put("reachable", false);
            out.put("message", "Не настроены api.home_assistant.url или api.home_assistant");
            return out;
        }

        String endpoint = normalizeBaseUrl(homeAssistantUrl) + "/api/";
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(homeAssistantToken);
            ResponseEntity<Map> response = restTemplate.exchange(endpoint, HttpMethod.GET, new HttpEntity<>(headers), Map.class);

            out.put("reachable", response.getStatusCode().is2xxSuccessful());
            out.put("status", response.getStatusCode().value());
            out.put("message", response.getStatusCode().is2xxSuccessful() ? "Home Assistant доступен" : "Home Assistant ответил с ошибкой");
            return out;
        } catch (Exception ex) {
            out.put("reachable", false);
            out.put("message", "Ошибка подключения к Home Assistant: " + ex.getMessage());
            return out;
        }
    }

    private String normalizeBaseUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
