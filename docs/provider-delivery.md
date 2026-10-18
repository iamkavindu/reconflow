# Provider delivery and recovery

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
