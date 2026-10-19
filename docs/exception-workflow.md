# Reconciliation exceptions and case workflow

Reconciliation owns the financial comparison. Cases own the human investigation
and resolution. A case references a reconciliation rather than copying its
financial records.

## Determinations

- A successful comparison becomes `MATCHED` and does not open a case.
- Related records that disagree become `REVIEW_REQUIRED`, with failed rule
  evaluations and individual discrepancy identities, values, and timestamps.
- Missing counterparts remain `PENDING` until the ten-minute matching window
  expires, then become `UNMATCHED`. Both missing-provider and missing-internal
  scenarios are supported.

The arrival window uses record-ingestion timestamps; the separate occurrence-time
rule compares the financial event timestamps. A record that arrived before the
deadline can still be processed successfully after that deadline. Matching and
expiry acquire the same tenant/reference lock before rechecking current records.

The scheduled expiry scan processes at most 100 pending records per pass. Its
default delay is five seconds and can be set with
`reconflow.reconciliation.expiry-scan-delay`. Tests disable scheduling and advance
an injected clock to check exact deadline behavior.

## Cases

`ReconciliationReviewRequired` and `ReconciliationUnmatched` cause a case to open
through Spring Modulith's transactional event listeners. PostgreSQL uniqueness on
tenant/reconciliation and atomic insertion make opening idempotent. Replaying an
event does not reset notes, status, or a completed resolution.

Supported transitions are `OPEN` to `UNDER_REVIEW`, `OPEN` to `RESOLVED`, and
`UNDER_REVIEW` to `RESOLVED`. Notes retain their author and timestamp. Resolution
retains its type, reason, actor, timestamp, and evidence references. Resolved cases
remain immutable. Optimistic locking rejects stale edits instead of losing notes.

Resolving a case preserves the reconciliation's original determination and source
financial records. Case lifecycle events provide the integration points for the
audit module.

Case mutations obtain identity and tenant from `CurrentActor` and require an
`ANALYST` or `ADMIN` role. The default actor implementation rejects mutations.
The authentication slice will supply the real authenticated actor; this slice
does not expose unauthenticated case-mutation endpoints. Evidence references are
stored human-provided references, not authority to retrieve arbitrary records.

Settlement/fee ingestion, authentication/API exposure, audit persistence, AI
investigation, search/dashboard, and the analyst UI are subsequent MVP slices.
