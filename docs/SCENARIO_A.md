# Scenario A — Core Audit Log Service

## The requirement, as given

Build a tamper-evident audit log service: a write API, a query API with filtering, a
hash-chain design making past records tamper-evident, and a verify endpoint that reports
whether the chain is intact and, if not, where the first break is.

## Decomposition

1. Domain model — what fields a record needs, and what makes it tamper-evident
   (`AuditEvent`, `ViolationType`)
2. Hashing logic, isolated from storage so it's independently testable (`HashingService`)
3. Write path — sequencing + chain-linking invariants (`AuditLogService.appendEvent`)
4. Read path — filtered, paginated queries (`AuditLogService.query`)
5. Verification — independently re-derive every hash and report divergence
   (`ChainVerificationService`)
6. HTTP boundary — endpoints with no update/delete capability (`AuditLogController`)
7. Config, error handling, docs, tests

## Ambiguities identified and resolved

- **"A hash of its own content" + "a hash of the preceding record" — one field or two?**
  Resolved as two separate fields (`contentHash`, `chainHash`) rather than one combined
  hash, specifically so verification can report *which* kind of tampering occurred
  (content edited vs. chain relinked) instead of a generic mismatch. See
  `docs/ARCHITECTURE.md` §3.
- **"Timestamp — caller-supplied or server-assigned; document your choice."** Resolved as
  server-assigned, because a caller-supplied timestamp would be trivially forgeable,
  undermining the log's entire purpose. Caller's own view of event time is still accepted
  and stored, but never treated as authoritative. See `docs/ARCHITECTURE.md` §3.
- **What happens under concurrent writers?** Not specified in the prompt. Resolved with an
  in-process lock (correct for one instance) plus a unique-index backstop, with the
  scaling limitation explicitly documented rather than silently assumed away. See
  `docs/ARCHITECTURE.md` §4.

## What's implemented vs. scoped out

**Implemented, in full:** write API, query API (all four filter dimensions + time range +
pagination), hash-chained storage, verify endpoint with violation classification and
first-violation reporting.

**Scoped out, explicitly:**
- AuthN/AuthZ — no requirement in the prompt specifies an identity model, and adding one
  arbitrarily would mean guessing at requirements rather than following the spec. Flagged
  as a real production gap in `docs/ARCHITECTURE.md` §7, not silently omitted.
- Multi-instance horizontal scaling — the prompt describes a single service to build and
  validate locally; three concrete scaling approaches are named in
  `docs/ARCHITECTURE.md` §4 rather than one being guessed at and half-implemented.
- Checkpointed/incremental verification — full re-verification is O(n), which is
  appropriate for a prototype's validation flow (write a handful of records, verify, tamper,
  verify again); optimizing this would be solving a scale problem the prompt doesn't pose.

## Validation

Proven correct three independent ways:
1. **Manual, via curl** — the exact write → verify → tamper → verify flow the assignment
   describes (see README.md).
2. **Manual, via Postman** — same flow, via `postman/audit-log-service.postman_collection.json`.
3. **Automated** — 11 passing unit tests (`HashingServiceTest`, `AuditLogServiceTest`,
   `ChainVerificationServiceTest`), including tests that directly simulate a payload edit,
   a deleted record, and a sophisticated "relink after deletion" attack.

A Testcontainers-based integration test automating the full flow against a real MongoDB
also exists but is currently disabled due to a documented, unrelated environment issue
(see `docs/ARCHITECTURE.md` §9).