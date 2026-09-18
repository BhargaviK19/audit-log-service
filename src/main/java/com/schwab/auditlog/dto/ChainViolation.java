package com.schwab.auditlog.dto;

import com.schwab.auditlog.model.ViolationType;

/** Describes exactly one detected break in the chain. */
public class ChainViolation {
    private long sequenceNumber;
    private String recordId;
    private ViolationType violationType;
    private String details;

    public ChainViolation() {}

    public ChainViolation(long sequenceNumber, String recordId, ViolationType violationType, String details) {
        this.sequenceNumber = sequenceNumber;
        this.recordId = recordId;
        this.violationType = violationType;
        this.details = details;
    }

    public long getSequenceNumber() { return sequenceNumber; }
    public String getRecordId() { return recordId; }
    public ViolationType getViolationType() { return violationType; }
    public String getDetails() { return details; }
}