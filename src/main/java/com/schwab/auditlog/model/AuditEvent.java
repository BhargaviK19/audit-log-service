package com.schwab.auditlog.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * A single, immutable audit record.
 *
 * Immutability is enforced structurally: this class has no setters used after
 * construction outside {@code AuditLogService}, and no repository method or
 * controller endpoint in this codebase performs an update or delete on this
 * collection. The unique index on sequenceNumber also prevents two records from
 * ever occupying the same slot in the chain.
 */
@Document(collection = "audit_events")
@CompoundIndexes({
        @CompoundIndex(name = "actor_time_idx", def = "{'actorId': 1, 'timestamp': 1}"),
        @CompoundIndex(name = "resource_idx", def = "{'resourceType': 1, 'resourceId': 1, 'timestamp': 1}"),
        @CompoundIndex(name = "event_type_time_idx", def = "{'eventType': 1, 'timestamp': 1}")
})
public class AuditEvent {

    @Id
    private String id;

    /**
     * Monotonically increasing position in the chain. 1-based.
     * Unique-indexed: this is the hard backstop described in AuditLogService -- even if two
     * writers ever raced past the in-process append lock, MongoDB itself will reject the
     * second insert at the same sequenceNumber.
     */
    @Indexed(unique = true, name = "sequence_number_unique_idx")
    private long sequenceNumber;

    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private Map<String, Object> payload;

    /**
     * Server-assigned authoritative timestamp (see docs/ARCHITECTURE.md, "Timestamp
     * handling"). This is what participates in the hash and in time-range queries.
     */
    private Instant timestamp;

    /**
     * Optional, caller-supplied business event time (e.g. "when the login actually
     * happened" per an upstream system's clock). Informational only: it is carried
     * inside the record and IS covered by contentHash (so it can't be silently altered
     * either), but it is never used for chain ordering or as the authoritative time.
     */
    private Instant clientSuppliedEventTime;

    /** SHA-256 hex digest of this record's own fields (see HashingService). */
    private String contentHash;

    /** The previous record's chainHash, or the configured genesis value for record #1. */
    private String previousHash;

    /** SHA-256 hex digest of (contentHash + previousHash). Links this record into the chain. */
    private String chainHash;

    public AuditEvent() {
    }

    public AuditEvent(String id, long sequenceNumber, String eventType, String actorId,
                      String resourceType, String resourceId, Map<String, Object> payload,
                      Instant timestamp, Instant clientSuppliedEventTime,
                      String contentHash, String previousHash, String chainHash) {
        this.id = id;
        this.sequenceNumber = sequenceNumber;
        this.eventType = eventType;
        this.actorId = actorId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.payload = payload;
        this.timestamp = timestamp;
        this.clientSuppliedEventTime = clientSuppliedEventTime;
        this.contentHash = contentHash;
        this.previousHash = previousHash;
        this.chainHash = chainHash;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public long getSequenceNumber() { return sequenceNumber; }
    public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }

    public String getResourceType() { return resourceType; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }

    public String getResourceId() { return resourceId; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }

    public Map<String, Object> getPayload() { return payload; }
    public void setPayload(Map<String, Object> payload) { this.payload = payload; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Instant getClientSuppliedEventTime() { return clientSuppliedEventTime; }
    public void setClientSuppliedEventTime(Instant clientSuppliedEventTime) { this.clientSuppliedEventTime = clientSuppliedEventTime; }

    public String getContentHash() { return contentHash; }
    public void setContentHash(String contentHash) { this.contentHash = contentHash; }

    public String getPreviousHash() { return previousHash; }
    public void setPreviousHash(String previousHash) { this.previousHash = previousHash; }

    public String getChainHash() { return chainHash; }
    public void setChainHash(String chainHash) { this.chainHash = chainHash; }
}