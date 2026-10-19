# Provider delivery and recovery

Analysts and administrators can send a simulated Stripe-like event with
`POST /api/provider-events/simulate`. The authenticated actor supplies tenant
scope; request bodies and headers cannot select another tenant. The simulator
validates references, currency precision, amount bounds, and source fields before
publication. It accepts the existing PAYMENT/REFUND and SUCCEEDED/FAILED values.

```json
{
  "providerEventId": "evt-demo-1001",
  "providerTransactionReference": "PAY-1001",
  "businessReference": "ORD-1001",
  "type": "PAYMENT",
  "amount": 100.00,
  "currency": "USD",
  "status": "SUCCEEDED",
  "occurredAt": "2026-10-19T10:00:00Z"
}
```

A `202` response acknowledges broker acceptance, with a delivery ID and
correlation ID. Ingestion and reconciliation remain asynchronous; use the
provider and reconciliation search APIs to observe their outcomes. Repeated
deliveries use the same provider event/transaction identity and preserve one
effective financial record. Each transport delivery has its own envelope ID.

The simulator publishes the existing version-one envelope through Kafka, keyed
by tenant/provider/logical provider reference. It never writes financial rows
directly. Producer acknowledgments, idempotence and bounded send timeouts provide
explicit delivery results. Disabled or uncertain delivery returns a safe `503`;
retry with the same source identity because a timeout does not prove rejection.

Compose now includes the same Kafka image used in integration tests. The `demo`
profile connects to its localhost listener. Broker logs use a named data volume;
PostgreSQL continues to own ingestion idempotency. CI also runs the HTTP-to-Kafka
integration tests against the actual Compose broker configuration. This remains
a simulator; it does not call a real provider or move funds.

Provider ingestion uses a Kafka record listener with three total delivery
attempts, separated by one-second blocking backoffs. A later record in the same
partition waits until the failed record succeeds or is recovered to the DLT.
The MVP provisions one partition; producers should use a stable stream key of
tenant, provider, and logical provider transaction reference.

Exhausted records are published to the corresponding partition of
`reconflow.provider-events.v1-dlt`, retaining the original payload and failure
headers. Publication errors propagate back to the listener recovery path, so a
failed DLT send does not acknowledge and silently discard the original record.
The application does not automatically consume and discard DLT records.

The database idempotency boundary protects financial effects when a record is
retried or replayed. Internal reconciliation and case coordination use Spring
Modulith's persisted event-publication registry.

Inspect DLT records before replaying them. Correct a recoverable configuration or
infrastructure problem first, then republish the original valid envelope to the
provider topic using its original provider event identity. Unsupported envelopes
require correction rather than blind repeated replay.
