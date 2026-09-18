package com.schwab.auditlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;

/** Inbound payload for POST /audit/events. There is no corresponding update DTO by design. */
public class CreateAuditEventRequest {

    @NotBlank(message = "eventType is required")
    private String eventType;

    @NotBlank(message = "actorId is required")
    private String actorId;

    @NotBlank(message = "resourceType is required")
    private String resourceType;

    @NotBlank(message = "resourceId is required")
    private String resourceId;

    @NotNull(message = "payload is required (use {} if there is no additional detail)")
    private Map<String, Object> payload;

    /**
     * Optional. The caller's own view of when the event occurred. Stored for context but
     * NOT used as the authoritative chain timestamp -- see docs/ARCHITECTURE.md.
     */
    private Instant clientSuppliedEventTime;

    public CreateAuditEventRequest() {}

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

    public Instant getClientSuppliedEventTime() { return clientSuppliedEventTime; }
    public void setClientSuppliedEventTime(Instant clientSuppliedEventTime) { this.clientSuppliedEventTime = clientSuppliedEventTime; }
}