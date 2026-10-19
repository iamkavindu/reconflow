# Operational search and dashboard

All list APIs require the existing bearer authentication. Tenant scope always
comes from active membership. Viewer, analyst and administrator roles can read
the same tenant's operational records.

| Endpoint | Optional exact filters | Date field |
| --- | --- | --- |
| `GET /api/transactions` | `transactionId`, `reference` | `occurredAt` |
| `GET /api/provider-transactions` | `providerTransactionId`, `providerReference`, `reference` | `occurredAt` |
| `GET /api/reconciliations` | `reconciliationId`, `transactionId`, `providerTransactionId`, `reference`, `status` | `startedAt` |
| `GET /api/cases` | `caseId`, `reconciliationId`, `status` | `openedAt` |

`reference` is the internal order/business reference. `providerReference` is the
external provider transaction identifier, such as `pi_1002`. UUID filters use
ReconFlow's resource IDs. Reconciliation provider-ID search includes matched,
provider-origin, and discrepancy candidate links.

Every list also accepts ISO-8601 UTC `from` (inclusive) and `to` (exclusive),
`limit` (default 50, maximum 100), and `offset` (default 0, maximum 10000). Results
are newest first by the date field, with descending UUID as a stable tie-breaker.
Filters combine with AND. Invalid bounds, IDs or status values return 400;
searching for another tenant's ID returns an empty list.

For example:

```text
/api/transactions?reference=ORD-1002&limit=25
/api/provider-transactions?providerReference=PAY-1002
/api/reconciliations?status=REVIEW_REQUIRED&from=2026-10-19T00:00:00Z&to=2026-10-20T00:00:00Z
/api/cases?status=OPEN&limit=25&offset=25
```

`GET /api/dashboard` returns six lifetime counts within the authenticated tenant:
`transactionsProcessed`, `matched`, `reviewRequired`, `unmatched`, `openCases`,
and `resolvedCases`. Processed counts internal payment/refund records. Decision
counts include provider-origin reconciliations; pending determinations are not
counted as a final outcome. Open cases include OPEN and UNDER_REVIEW. Resolving
a case changes case counts while preserving the original reconciliation result.

Module-owned read ports retain source ownership. Reporting composes those ports
inside one read-only, repeatable-read database snapshot. Each list is likewise
consistent within its request; offset pagination across requests can shift when
new records arrive. These APIs provide the required basic operational visibility
without advanced reporting or financial accounting.
