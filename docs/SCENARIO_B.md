# Scenario B — Retention and Redaction (Not Implemented)

## The requirement, as given

Extend Scenario A with: a retention policy that can archive/soft-delete old records
without the verify endpoint reporting a false break; structured redaction of sensitive
payload fields without breaking the hash chain; and a bulk export endpoint that produces
an independently verifiable bundle for a given resource or actor.

## Decomposition (as it would be built)

1. Redesign the hashing scheme to commit to each payload field individually (e.g. a
   Merkle tree over fields, or per-field salted hashes), instead of one flat canonical-JSON
   hash -- a prerequisite for (2) below.
2. Redaction: replace a field's value with a tombstone while preserving its commitment, so
   verification still passes without the original value being recoverable.
3. Archival: move old records to cold storage while keeping `ChainVerificationService`
   able to treat "present in cold storage with a matching hash" as equivalent to "present."
4. Bulk export: package a resource's/actor's full record range plus enough chain metadata
   (the record immediately before the range, at minimum) for a recipient to verify the
   bundle independently, without access to the full live chain.

## Ambiguities identified

- **What counts as "sensitive"?** Not specified — presumably configurable per field, but
  the requirement doesn't say who configures it or at what granularity (per event type,
  per field name, per value pattern).
- **Retention window** — "configurable," but no default or unit is given (days? years?
  regulatory minimum?).
- **Redaction reversibility** — does compliance ever need to *un-redact* (e.g. under legal
  order), or is it meant to be permanent? This materially changes the design (a
  permanently-discarded value vs. one recoverable via an escrowed key).

## Why this is scoped out of this submission, explicitly

Redaction is not a small addition on top of Scenario A's hashing scheme — it requires a
*different* hashing scheme (per-field commitments rather than one flat content hash),
because Scenario A's `contentHash` covers the entire canonicalized payload as one unit:
redacting any single field would invalidate that hash exactly as any other tamper would.
Implementing redaction correctly means redesigning `HashingService`'s contract, which is a
Scenario B deliverable in its own right. Half-implementing it inside Scenario A's existing
scheme (e.g. blanking a field and leaving the old hash in place) would either silently
break tamper-evidence or produce something that looks like it works but doesn't actually
satisfy "without breaking the hash chain" -- worse than not attempting it.

Given the time available, the judgment call made here was: make Scenario A's core
genuinely solid (correct concurrency handling, real filtering, precise violation
classification, a documented and fixed timestamp bug, a full test suite) rather than
producing a partial or fragile version of B on top of a less-polished A.

## What a real implementation would look like (design only)

- **Hashing**: replace the single `contentHash` with a Merkle root over per-field hashes
  (`sha256(fieldName + salt + value)` per field, then a tree/concatenated hash over those).
  `chainHash` computation is otherwise unchanged.
- **Redaction**: a dedicated `POST /audit/events/{id}/redact` endpoint that replaces a
  field's stored value with a tombstone marker, while keeping its per-field hash
  unchanged, so the Merkle root (and therefore `contentHash` and everything downstream)
  still verifies.
- **Archival**: a `status` field (`ACTIVE`/`ARCHIVED`) plus a second, less-frequently-
  queried collection or storage tier for archived records; `ChainVerificationService`
  reads from both sources in sequence order rather than one collection.
- **Bulk export**: `GET /audit/export?resourceId=...` returning the matching records plus
  the `chainHash` of the record immediately preceding the range, so a recipient can verify
  the exported slice links correctly to a known-good prior state without needing the
  entire chain.

None of this is implemented in the current codebase. This document exists so the design
reasoning is available for the live defense, per the assignment's explicit allowance for
"a well-reasoned partial implementation with a documented scope boundary."