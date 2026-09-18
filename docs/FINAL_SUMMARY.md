# Final Engineering Summary

## Plan and rationale

The assignment's core requirement is trust: an audit log is only as good as its resistance
to tampering, including by someone with direct database access. The plan was to make that
property genuinely solid for Scenario A before spending time on B or C, rather than
spreading effort thin across all three at a shallower level of correctness.

Execution order: domain model → isolated hashing logic → write/query service →
verification service → HTTP layer → config/error handling → docs → tests (unit, then a
documented integration-test attempt) → manual validation (curl, then Postman) →
requirement analysis write-ups for B and C once A was solid and proven.

## Artifacts delivered

- Working Spring Boot + MongoDB service (`src/main/java/com/schwab/auditlog/`)
- `README.md` — setup, run instructions, API reference, manual validation walkthrough
- `docs/ARCHITECTURE.md` — design rationale, data model, concurrency trade-offs,
  verification design, security notes, risks table, known limitations
- `docs/SCENARIO_A.md`, `docs/SCENARIO_B.md`, `docs/SCENARIO_C.md` — requirement
  decomposition, ambiguities, assumptions, and scope decisions for each scenario
- `postman/audit-log-service.postman_collection.json` — importable manual test flow
- 12 automated tests (11 passing unit tests + 1 documented, disabled integration test)
- Swagger/OpenAPI docs at runtime (`/swagger-ui/index.html`)
- `AI_USAGE_LOG.md` — traceability of AI-assisted work
- `ATTESTATION.md`

## Key risks and trade-offs (full detail in ARCHITECTURE.md §4, §7, §8)

| Area | Decision | Trade-off accepted |
|---|---|---|
| Concurrency | In-process lock + unique index backstop | Correct for one instance; horizontal scaling needs a different write path (three options identified, none implemented) |
| Timestamps | Server-assigned, millisecond-truncated | Caller can't forge event time; a real bug was found and fixed here (see ARCHITECTURE.md §3) |
| Verification cost | Full O(n) walk per call | Simple and correct for a prototype; checkpointing identified as the scale path, not implemented |
| Security | No authN/authZ | Explicitly out of scope for the prompt as given; flagged as a real production gap, not silently ignored |

## Assumptions made

- Single-instance deployment is the target for this exercise (stated explicitly rather
  than assumed silently — see ARCHITECTURE.md §4).
- "Audit access" in Scenario C means auditing *actions* on account data, not tracking
  every read/view across other systems (see SCENARIO_C.md for the full reasoning).
- A JSON API response is an acceptable "report" format for Scenario C's internal use case.

## Limitations

- Scenarios B and C are documented and designed, not implemented (B in full; C as a
  small, reusable extension of what already exists) — see their respective docs for why,
  in each case.
- No authentication/authorization anywhere in the service.
- The Testcontainers integration test is disabled due to a local Docker Desktop version
  compatibility issue, unrelated to the service's own code (see ARCHITECTURE.md §9); the
  same scenario is proven correct via curl, Postman, and unit tests instead.
- Full-chain verification does not scale past a prototype-sized collection without the
  checkpointing approach described but not built.
