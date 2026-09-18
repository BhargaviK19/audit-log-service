package com.schwab.auditlog.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/**
 * All hashing / canonicalization logic lives here, isolated from persistence and HTTP
 * concerns so it can be unit tested in complete isolation and so there is exactly one
 * place that defines "what a record's hash means."
 *
 * Algorithm choice: SHA-256.
 *  - Cryptographically strong pre-image and collision resistance for our threat model
 *    (detecting deliberate or accidental modification of stored fields).
 *  - Fast, standard, available in every JVM without extra dependencies.
 *  - We are not choosing it for password storage (no need for a slow/salted KDF here) --
 *    the threat here is "did this document change since it was written", not "can an
 *    attacker brute-force the input from the hash", though SHA-256 resists that too.
 *
 * Canonicalization: field order matters for hash reproducibility. We build a fixed,
 * pipe-delimited string of top-level scalar fields (so two records with the same values
 * always hash identically regardless of how the object was constructed), and separately
 * canonicalize the payload map by recursively sorting all map keys before JSON-serializing
 * it, so {"a":1,"b":2} and {"b":2,"a":1} always hash the same way.
 */
@Service
public class HashingService {

    private static final String ALGORITHM = "SHA-256";
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Computes the content hash of a record from its own fields (everything except
     * previousHash/chainHash, which are chain-linkage concerns, not content).
     */
    public String computeContentHash(long sequenceNumber, String eventType, String actorId,
                                     String resourceType, String resourceId,
                                     Map<String, Object> payload, Instant timestamp,
                                     Instant clientSuppliedEventTime) {
        StringBuilder sb = new StringBuilder();
        sb.append(sequenceNumber).append('|');
        sb.append(nullSafe(eventType)).append('|');
        sb.append(nullSafe(actorId)).append('|');
        sb.append(nullSafe(resourceType)).append('|');
        sb.append(nullSafe(resourceId)).append('|');
        sb.append(timestamp == null ? "" : timestamp.toString()).append('|');
        sb.append(clientSuppliedEventTime == null ? "" : clientSuppliedEventTime.toString()).append('|');
        sb.append(canonicalizePayload(payload));
        return sha256Hex(sb.toString());
    }

    /** Computes the chain-linking hash: sha256(contentHash + previousHash). */
    public String computeChainHash(String contentHash, String previousHash) {
        return sha256Hex(nullSafe(contentHash) + nullSafe(previousHash));
    }

    /** Deterministic JSON of the payload map with all keys (at every depth) sorted. */
    public String canonicalizePayload(Map<String, Object> payload) {
        try {
            Object canonical = canonicalize(payload == null ? Collections.emptyMap() : payload);
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to canonicalize payload for hashing", e);
        }
    }

    @SuppressWarnings("unchecked")
    private Object canonicalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sorted.put(String.valueOf(entry.getKey()), canonicalize(entry.getValue()));
            }
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(canonicalize(item));
            }
            return result;
        }
        return value;
    }

    public String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(ALGORITHM);
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM implementation; this is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}