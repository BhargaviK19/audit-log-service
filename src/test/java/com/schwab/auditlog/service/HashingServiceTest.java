package com.schwab.auditlog.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HashingServiceTest {

    private final HashingService hashingService = new HashingService();

    @Test
    void sameInputsAlwaysProduceTheSameContentHash() {
        Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, Object> payload = Map.of("account", "12345", "amount", 100);

        String h1 = hashingService.computeContentHash(1, "USER_LOGIN", "actor-1", "USER", "res-1", payload, ts, null);
        String h2 = hashingService.computeContentHash(1, "USER_LOGIN", "actor-1", "USER", "res-1", payload, ts, null);

        assertEquals(h1, h2);
        assertEquals(64, h1.length(), "SHA-256 hex digest should be 64 characters");
    }

    @Test
    void payloadKeyOrderDoesNotAffectTheHash() {
        Instant ts = Instant.parse("2026-01-01T00:00:00Z");

        Map<String, Object> payloadOrderA = new LinkedHashMap<>();
        payloadOrderA.put("a", 1);
        payloadOrderA.put("b", 2);

        Map<String, Object> payloadOrderB = new LinkedHashMap<>();
        payloadOrderB.put("b", 2);
        payloadOrderB.put("a", 1);

        String hashA = hashingService.computeContentHash(1, "EVT", "actor", "TYPE", "res", payloadOrderA, ts, null);
        String hashB = hashingService.computeContentHash(1, "EVT", "actor", "TYPE", "res", payloadOrderB, ts, null);

        assertEquals(hashA, hashB, "Canonicalization must sort map keys so key order is irrelevant");
    }

    @Test
    void changingAnyFieldChangesTheHash() {
        Instant ts = Instant.parse("2026-01-01T00:00:00Z");
        Map<String, Object> payload = Map.of("k", "v");

        String original = hashingService.computeContentHash(1, "EVT", "actor", "TYPE", "res", payload, ts, null);
        String tamperedPayload = hashingService.computeContentHash(1, "EVT", "actor", "TYPE", "res", Map.of("k", "TAMPERED"), ts, null);
        String tamperedActor = hashingService.computeContentHash(1, "EVT", "actor-DIFFERENT", "TYPE", "res", payload, ts, null);
        String tamperedSequence = hashingService.computeContentHash(2, "EVT", "actor", "TYPE", "res", payload, ts, null);

        assertNotEquals(original, tamperedPayload);
        assertNotEquals(original, tamperedActor);
        assertNotEquals(original, tamperedSequence);
    }

    @Test
    void chainHashDependsOnBothContentHashAndPreviousHash() {
        String content = hashingService.sha256Hex("content");
        String chainA = hashingService.computeChainHash(content, "prev-A");
        String chainB = hashingService.computeChainHash(content, "prev-B");

        assertNotEquals(chainA, chainB, "Changing previousHash must change the chain hash even if content is identical");
    }
}