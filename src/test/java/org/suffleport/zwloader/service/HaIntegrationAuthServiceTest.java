package org.suffleport.zwloader.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HaIntegrationAuthServiceTest {

    @Test
    void webhookTokenBlank_disablesCheck() {
        HaIntegrationAuthService svc = new HaIntegrationAuthService(" ", " ");
        assertDoesNotThrow(() -> svc.requireValidToken(null));
        assertDoesNotThrow(() -> svc.requireValidToken(""));
        assertDoesNotThrow(() -> svc.requireValidToken("anything"));
    }

    @Test
    void webhookTokenConfigured_requiresToken() {
        HaIntegrationAuthService svc = new HaIntegrationAuthService("secret", " ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.requireValidToken(""));
        assertTrue(ex.getMessage().toLowerCase().contains("токен"));
    }

    @Test
    void webhookTokenConfigured_rejectsWrongToken() {
        HaIntegrationAuthService svc = new HaIntegrationAuthService("secret", " ");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> svc.requireValidToken("wrong"));
        assertTrue(ex.getMessage().toLowerCase().contains("невер"));
    }

    @Test
    void webhookTokenConfigured_acceptsExactToken() {
        HaIntegrationAuthService svc = new HaIntegrationAuthService("secret", " ");
        assertDoesNotThrow(() -> svc.requireValidToken("secret"));
    }
}
