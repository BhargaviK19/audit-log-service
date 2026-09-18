package com.schwab.auditlog.dto;

import com.schwab.auditlog.model.AuditEvent;

import java.time.Instant;
import java.util.Map;

/** Outbound representation of a stored record, including the chain metadata callers need to verify it. */
public class AuditEventResponse {
    private String id;
    private long sequenceNumber;
    private String eventType;
    private String actorId;
    private String resourceType;
    private String resourceId;
    private Map<String, Object> payload;
    private Instant timestamp;
    private Instant clientSuppliedEventTime;
    private String contentHash;
    private String previousHash;
    private String chainHash;

    public static AuditEventResponse from(AuditEvent e) {
        AuditEventResponse r = new AuditEventResponse();
        r.id = e.getId();
        r.sequenceNumber = e.getSequenceNumber();
        r.eventType = e.getEventType();
        r.actorId = e.getActorId();
        r.resourceType = e.getResourceType();
        r.resourceId = e.getResourceId();
        r.payload = e.getPayload();
        r.timestamp = e.getTimestamp();
        r.clientSuppliedEventTime = e.getClientSuppliedEventTime();
        r.contentHash = e.getContentHash();
        r.previousHash = e.getPreviousHash();
        r.chainHash = e.getChainHash();
        return r;
    }

    public String getId() { return id; }
    public long getSequenceNumber() { return sequenceNumber; }
    public String getEventType() { return eventType; }
    public String getActorId() { return actorId; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public Map<String, Object> getPayload() { return payload; }
    public Instant getTimestamp() { return timestamp; }
    public Instant getClientSuppliedEventTime() { return clientSuppliedEventTime; }
    public String getContentHash() { return contentHash; }
    public String getPreviousHash() { return previousHash; }
    public String getChainHash() { return chainHash; }
}