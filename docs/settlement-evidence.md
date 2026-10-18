# Provider settlement evidence

Provider-owned settlements retain the provider's reported gross, fee and net
totals, currency, settlement time and source transaction lines. The application
checks that reported totals agree with the lines and that gross minus fees equals
net. It does not calculate a fee tariff, create a payout or post accounting entries.
Signed gross/net lines can represent refunds or adjustments; reported fees are
nonnegative. Values must fit the currency precision and storage bounds.

Imports are identified by tenant, provider and provider settlement reference.
Concurrent identical imports return the original record and publish one audit
event. A conflicting payload using that identity returns 409 and cannot overwrite
source evidence. Lines can arrive before normalized provider records. They link
by tenant, provider and provider transaction reference, never by reference alone.

`POST /api/settlements` imports reported totals and up to 1000 lines for an analyst
or administrator's authenticated tenant. `GET /api/settlements/{id}` reads one.
`GET /api/provider-transactions/{id}/settlements` and
`GET /api/reconciliations/{id}/settlements` expose the linked supporting evidence.
OpenAPI describes the request and response records.

For the agreed fee scenario, the internal payment is USD 100.00 and the provider
record reports USD 96.80. If an imported USD settlement line explicitly reports
gross 100.00, fee 3.20 and net 96.80, reconciliation records both AMOUNT_MISMATCH
and FEE_VARIANCE and remains REVIEW_REQUIRED. The fee finding cites the settlement
identity. It does not establish that the reported fee was contractually correct
or resolve the case; the analyst reviews that evidence.

Late settlement evidence is available to investigation and human review without
rewriting a completed deterministic decision or reopening a resolved case. The
reconciliation's original findings and decision time remain unchanged. A missing
or unbalanced fee explanation is never guessed from an amount difference.

Integration tests cover the fee example and case opening, concurrent idempotent
imports, source conflict rejection, audit delivery, late evidence, source-link
tenant isolation, duplicate lines and invalid currency precision.
