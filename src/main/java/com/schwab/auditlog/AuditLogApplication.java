package com.schwab.auditlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the tamper-evident audit log service.
 *
 * Design intent (see docs/ARCHITECTURE.md for full detail):
 *  - Every write is appended to a hash chain; there is no update/delete endpoint anywhere
 *    in this codebase by design (not just by convention).
 *  - Each record stores: its own content hash, the previous record's chain hash, and its
 *    own chain hash (content hash + previous chain hash). This makes any retroactive edit,
 *    reordering, or deletion detectable by GET /audit/verify.
 */
@SpringBootApplication
public class AuditLogApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditLogApplication.class, args);
    }
}