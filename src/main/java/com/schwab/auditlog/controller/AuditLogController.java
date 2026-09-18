package com.schwab.auditlog.controller;

import com.schwab.auditlog.dto.AuditEventResponse;
import com.schwab.auditlog.dto.CreateAuditEventRequest;
import com.schwab.auditlog.dto.PageResponse;
import com.schwab.auditlog.dto.VerificationResponse;
import com.schwab.auditlog.model.AuditEvent;
import com.schwab.auditlog.service.AuditLogService;
import com.schwab.auditlog.service.ChainVerificationService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Instant;

/**
 * Note what is NOT here: there is no PUT, no PATCH, no DELETE mapping anywhere in this
 * class, and no method in AuditLogService that performs one. Append-only is enforced by
 * omission, not by a guard clause that could be bypassed.
 */
@RestController
@RequestMapping("/audit")
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final ChainVerificationService chainVerificationService;

    public AuditLogController(AuditLogService auditLogService, ChainVerificationService chainVerificationService) {
        this.auditLogService = auditLogService;
        this.chainVerificationService = chainVerificationService;
    }

    @PostMapping("/events")
    public ResponseEntity<AuditEventResponse> append(@Valid @RequestBody CreateAuditEventRequest request) {
        AuditEvent saved = auditLogService.appendEvent(request);
        AuditEventResponse body = AuditEventResponse.from(saved);
        return ResponseEntity.created(URI.create("/audit/events/" + saved.getId())).body(body);
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<AuditEventResponse> getById(@PathVariable String id) {
        return auditLogService.findById(id)
                .map(AuditEventResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    @GetMapping("/events")
    public PageResponse<AuditEventResponse> query(
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {

        PageResponse<AuditEvent> result = auditLogService.query(actorId, resourceType, resourceId, eventType, from, to, page, size);
        var mapped = result.getItems().stream().map(AuditEventResponse::from).toList();
        return new PageResponse<>(mapped, result.getPage(), result.getSize(), result.getTotalElements());
    }

    @GetMapping("/verify")
    public VerificationResponse verify() {
        return chainVerificationService.verify();
    }
}