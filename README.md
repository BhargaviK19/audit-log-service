# Audit Log Service

A tamper-evident, append-only audit log service. Records form a SHA-256 hash chain: any
retroactive edit, reorder, or deletion of a past record is detectable via `GET /audit/verify`.

This README covers Scenario A (core service) from the assignment. See `docs/ARCHITECTURE.md`
for the detailed design write-up and `AI_USAGE_LOG.md` for how AI was used while building this.

## Stack

- Java 17 (built and tested on Java 21), Spring Boot 3.3 (Web, Data MongoDB, Validation, Actuator)
- MongoDB 7 (via Docker Compose for local dev)
- Maven
- JUnit 5 + Mockito for unit tests (no external services required)
- Testcontainers for an integration test (real, throwaway MongoDB via Docker)
- springdoc-openapi (Swagger UI)

## Prerequisites

- JDK 17+
- Maven 3.9+
- Docker Desktop (for MongoDB, and for the optional Testcontainers integration test)

## Run it locally

```bash
# 1. Start MongoDB
docker compose up -d

# 2. Run the service
mvn spring-boot:run
```

The service starts on `http://localhost:8080`. Override with env vars if needed:

```bash
MONGODB_URI=mongodb://localhost:27017/auditlog SERVER_PORT=8080 mvn spring-boot:run
```

## Run the tests

```bash
mvn test
```

12 tests total:
- `HashingServiceTest` (4) — canonicalization, determinism, sensitivity to every field
- `AuditLogServiceTest` (3) — sequencing, chain-linking, server-assigned timestamps
- `ChainVerificationServiceTest` (4) — directly simulates a payload edit, a deleted
  record, and a sophisticated "relink after deletion" attack, confirming each is caught
  and correctly classified
- `AuditLogIntegrationTest` (1) — Testcontainers-based, exercises the full flow against a
  real, throwaway MongoDB. **Currently disabled** on this development machine due to a
  Docker Desktop version compatibility issue — see `docs/ARCHITECTURE.md` §9. The scenario
  it automates has been independently confirmed via curl, Postman, and the unit tests above.

None of the unit tests need a live MongoDB. Only the integration test does, and it manages
its own (via Testcontainers), separate from the `docker-compose.yml` instance used for
manual runs.

## API documentation (Swagger)

With the app running, open:
http://localhost:8080/swagger-ui/index.html


Every endpoint is listed with its parameters and a "Try it out" button to fire real
requests from the browser. The raw OpenAPI spec is also available at
`http://localhost:8080/v3/api-docs`.

## Postman collection

A ready-to-import collection is at `postman/audit-log-service.postman_collection.json`.
Import it into Postman and run requests 1 → 5 in order to walk through the full
write → query → verify flow, or use it to demo tamper-evidence live: run request 5
("Verify Chain"), edit a record directly via `mongosh` (see below), then run request 5
again and watch it flip to `"intact": false`.

## API

### `POST /audit/events` — append a record

```bash
curl -s -X POST localhost:8080/audit/events \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "USER_LOGIN",
    "actorId": "user-42",
    "resourceType": "USER_ACCOUNT",
    "resourceId": "acct-42",
    "payload": { "ip": "10.0.0.7", "mfa": true }
  }'
```

Returns `201 Created` with the stored record, including `sequenceNumber`, `contentHash`,
`previousHash`, and `chainHash`. There is no `PUT`/`PATCH`/`DELETE` for this resource
anywhere in the codebase.

### `GET /audit/events` — query with filters + pagination

```bash
curl -s "localhost:8080/audit/events?actorId=user-42&page=0&size=20"
curl -s "localhost:8080/audit/events?resourceType=USER_ACCOUNT&resourceId=acct-42"
curl -s "localhost:8080/audit/events?eventType=USER_LOGIN&from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z"
```

All filters are optional and combine with AND: `actorId`, `resourceType`, `resourceId`,
`eventType`, `from`/`to` (ISO-8601 instants). `page` (0-based) and `size` (default 20, max
200) control pagination.

### `GET /audit/verify` — walk the whole chain

```bash
curl -s localhost:8080/audit/verify
```

```json
{
  "intact": true,
  "recordsChecked": 42,
  "verifiedAt": "2026-09-17T10:00:00Z",
  "violations": [],
  "firstViolation": null
}
```

### End-to-end proof of tamper evidence 

```bash
# 1. Write a few events
curl -s -X POST localhost:8080/audit/events -H "Content-Type: application/json" \
  -d '{"eventType":"RECORD_UPDATED","actorId":"svc-a","resourceType":"ACCOUNT","resourceId":"a-1","payload":{"balance":100}}'
curl -s -X POST localhost:8080/audit/events -H "Content-Type: application/json" \
  -d '{"eventType":"RECORD_UPDATED","actorId":"svc-a","resourceType":"ACCOUNT","resourceId":"a-1","payload":{"balance":150}}'

# 2. Confirm the chain is intact
curl -s localhost:8080/audit/verify

# 3. Tamper directly in the data store (bypassing the API entirely)
docker exec -it audit-log-mongo mongosh auditlog --eval 'db.audit_events.updateOne({sequenceNumber: 1}, {$set: {"payload.balance": 999999}})'

# 4. Verify again -- this must now report the break
curl -s localhost:8080/audit/verify
```

Step 4 will report `"intact": false`, with `firstViolation.sequenceNumber = 1` and
`violationType = "CONTENT_HASH_MISMATCH"`.

## What's implemented vs. scoped out

Implemented (Scenario A, in full): write API, query API with all four filter dimensions
plus time range and pagination, hash-chained storage, and the verify endpoint with
first-violation reporting and violation classification. Also added beyond the minimum:
Swagger/OpenAPI docs, a Postman collection, and a layered test suite (unit + a documented,
currently-disabled integration test).

Scoped out of this submission: Scenario B (retention/archival, structured redaction,
bulk export bundle) and Scenario C (compliance reporting) are not implemented here — this
submission focuses on making Scenario A genuinely solid. See `docs/ARCHITECTURE.md` §6 for
how Scenario B would be approached.

## Known limitations 

- **Single-writer concurrency model.** Appends are serialized with an in-process lock,
  correct for one running instance, not for multiple. A unique index on `sequenceNumber`
  is a hard backstop against silent corruption if that's ever violated.
- **No AuthN/AuthZ.** Out of scope for the prototype; see `docs/ARCHITECTURE.md` §7.
- **Full-chain verification is O(n).** Fine for a prototype; see `docs/ARCHITECTURE.md` §5.
- **Integration test disabled on this dev machine** — Testcontainers/Docker Desktop
  version compatibility issue, not a code defect. See `docs/ARCHITECTURE.md` §9.