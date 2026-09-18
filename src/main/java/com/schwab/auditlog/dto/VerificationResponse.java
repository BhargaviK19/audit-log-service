package com.schwab.auditlog.dto;

import java.time.Instant;
import java.util.List;

/** Response for GET /audit/verify. */
public class VerificationResponse {
    private boolean intact;
    private long recordsChecked;
    private Instant verifiedAt;
    /** Empty when intact == true. Only the FIRST inconsistency is guaranteed to be meaningful
     *  (everything after it is likely a cascading effect), but we report all findings for context. */
    private List<ChainViolation> violations;
    private ChainViolation firstViolation;

    public VerificationResponse() {}

    public VerificationResponse(boolean intact, long recordsChecked, Instant verifiedAt,
                                List<ChainViolation> violations, ChainViolation firstViolation) {
        this.intact = intact;
        this.recordsChecked = recordsChecked;
        this.verifiedAt = verifiedAt;
        this.violations = violations;
        this.firstViolation = firstViolation;
    }

    public boolean isIntact() { return intact; }
    public long getRecordsChecked() { return recordsChecked; }
    public Instant getVerifiedAt() { return verifiedAt; }
    public List<ChainViolation> getViolations() { return violations; }
    public ChainViolation getFirstViolation() { return firstViolation; }
}