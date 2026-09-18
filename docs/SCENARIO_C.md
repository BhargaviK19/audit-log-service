# Scenario C — Ambiguous Requirement: Compliance Reporting

## The requirement, as given

> "Regulators need to be able to audit access to client account data."

This is intentionally under-specified, per the assignment. Below is the clarification
process worked through before any design or implementation decisions were made.

## Central ambiguity

The word "audit access" can mean two materially different things, leading to very
different scopes of work:

**(a) Audit of actions** — regulators need visibility into what was *done* to client
accounts: updates, logins tied to an account, transactions, etc. This is already answered
by the service built in Scenario A: `GET /audit/events` filtered by
`resourceType=ACCOUNT`/`resourceId`, backed by `GET /audit/verify` to prove the record
hasn't been tampered with.

**(b) Audit of mere access/viewing** — regulators need to know who merely *looked at* an
account's data (e.g. a support rep opening an account screen without changing anything).
This is a genuinely larger problem: this audit service only knows about events that are
explicitly reported to it via `POST /audit/events`. It has no visibility into someone
viewing a screen in some other system unless that system is instrumented to emit an event
here. Building (b) properly means an integration effort across other systems, not a change
to this service alone.

This distinction is the single most consequential ambiguity in the requirement, since it
determines whether the work is "expose what's already captured" or "instrument new
systems to capture something not currently captured at all."

## Other ambiguities identified

- **Which regulators?** Internal compliance staff acting as a stand-in for a regulator, or
  an external body with its own submission format and legal retention requirements?
- **Access model:** self-service query access for regulators directly, or an internal
  report that compliance staff generate and hand over on request?
- **Output format:** raw JSON is fine internally, but a regulator likely expects a
  structured, human-readable, or externally-verifiable document.
- **Retention period:** not addressed anywhere in Scenario A; a real compliance
  requirement would specify how long records must be kept and queryable.

## Assumptions made (in place of asking a product manager directly)

1. Interpretation (a) — audit of *actions* on client account data — is the in-scope
   reading, since it's answerable with what's already built and is the more common
   meaning of "audit" in a financial compliance context (an audit trail of changes, not a
   surveillance log of every screen view).
2. The consumer is internal compliance staff producing a report on request, not a
   regulator with direct self-service system access — self-service external access would
   raise its own authentication/authorization scope (see Scenario A's Security Notes,
   ARCHITECTURE.md §7) that is a separate concern from this reporting capability itself.
3. A JSON report is an acceptable output format for this exercise; a real deployment would
   likely need a PDF/CSV export step downstream, treated as a presentation-layer concern
   layered on top of this data, not a change to the audit log's core data model.

## Clarified requirement (the version actually designed against)

> Compliance staff can produce, for a given client account and time range, a complete and
> independently verifiable record of every event this audit service has captured for that
> account — for internal use and for handing to a regulator on request. Capturing
> read-only "viewing" access from other systems (interpretation (b) above) is explicitly
> out of scope for this service.

## Design decision

Add one new endpoint, `GET /audit/compliance-report`, that reuses the existing query and
verification logic rather than introducing new hashing or storage concepts: it filters
events by `resourceType`/`resourceId`/time range (identical filters to
`GET /audit/events`), and bundles the result together with a `GET /audit/verify`-style
integrity check and report metadata (when it was generated, what filters were applied),
so the response is a self-contained artifact rather than a bare list of records.

## What's implemented vs. scoped out

**Implemented:** `GET /audit/compliance-report` (see below).

**Scoped out, explicitly:**
- Interpretation (b), cross-system "viewing" instrumentation — a separate integration
  effort with its own system owners, not something this service can address alone.
- Regulator self-service authentication — depends on decisions (which regulators, what
  access model) that weren't available to make here; see ARCHITECTURE.md §7 for the
  general authN/authZ gap this shares with the rest of the service.
- Any specific external regulatory submission format (e.g. a particular agency's required
  schema) — not specified in the original requirement, and would need to be gathered
  before building rather than guessed at.