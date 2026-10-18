# Automatic reconciliation — first matching slice

Internal and provider records remain separate aggregates. Reconciliation accesses
them through tenant-scoped candidate query interfaces, never through another
module's persistence classes.

Both `TransactionRecorded` and `ProviderTransactionRecorded` trigger a matching
attempt. Spring Modulith runs each listener in its own transaction. A PostgreSQL
transaction-scoped advisory lock serializes attempts for a tenant/reference pair;
database uniqueness also prevents reusing a provider transaction in two matches.

A single internal candidate and a single provider candidate can become `MATCHED`
when all of these deterministic evaluations pass:

- Exact business reference.
- Numerically equal amount; no implicit tolerance or rounding.
- Same currency.
- Same transaction type (`PAYMENT` or `REFUND`).
- Internal `RECORDED` and provider `SUCCEEDED` statuses.
- At most ten minutes between financial occurrence timestamps.

The ten-minute occurrence-time policy is separate from the existing ten-minute
counterpart-arrival window. The application records the provider transaction ID,
decision timestamp, and expected/actual result for every evaluated rule. It emits
`ReconciliationMatched` once. A successful match does not open a case or modify
either source financial record.

In this incremental slice, missing, ambiguous, expired, and conflicting candidates
do not auto-match. They remain pending until the discrepancy/expiry slices add
their required `REVIEW_REQUIRED` / `UNMATCHED` transitions and case creation.
This is not the final behavior for those MVP scenarios.
