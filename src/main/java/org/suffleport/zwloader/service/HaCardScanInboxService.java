package org.suffleport.zwloader.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class HaCardScanInboxService {

    public record CardScanPayload(String uid, String status, String personName, OffsetDateTime receivedAt) {}

    private CardScanPayload lastPayload;
    private OffsetDateTime registrationActiveUntil;

    public synchronized void startRegistrationWindow(int ttlSeconds) {
        int safeTtl = Math.max(5, Math.min(ttlSeconds, 300));
        this.registrationActiveUntil = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(safeTtl);
    }

    public synchronized void stopRegistrationWindow() {
        this.registrationActiveUntil = null;
    }

    public synchronized boolean isRegistrationWindowActive() {
        if (registrationActiveUntil == null) {
            return false;
        }
        if (registrationActiveUntil.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            registrationActiveUntil = null;
            return false;
        }
        return true;
    }

    public synchronized void push(String uid, String status, String personName) {
        if (uid == null || uid.isBlank()) {
            return;
        }
        this.lastPayload = new CardScanPayload(uid, status, personName, OffsetDateTime.now());
    }

    public synchronized boolean pushIfRegistrationWindowActive(String uid, String status, String personName) {
        if (!isRegistrationWindowActive()) {
            return false;
        }
        push(uid, status, personName);
        // one-shot: close window after the first accepted scan
        stopRegistrationWindow();
        return true;
    }

    public synchronized CardScanPayload poll() {
        CardScanPayload payload = this.lastPayload;
        this.lastPayload = null;
        return payload;
    }
}
