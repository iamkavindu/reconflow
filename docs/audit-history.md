# Audit history

The audit module subscribes to committed transaction, provider, reconciliation
and case events through Spring Modulith's persistent publication registry. An
append is committed in the listener's own transaction; failed listeners remain
eligible for redelivery. A unique tenant/event constraint makes repeated delivery
produce one audit entry.

Entries retain the source event identity and occurrence time, recording time,
tenant, resource identity, action, and available correlation/causation IDs. Human
case actions carry the actor stored by the case service. New internal transaction
events capture the authenticated actor before leaving the request transaction.
Provider ingestion, automatic decisions, case opening and historical events
without an actor keep it absent. The consumer never reads a request security
context or invents an actor for background work. Historical transaction events
remain deserializable when the new actor field is absent.

The history is append-only: there is no update/delete API or repository operation,
and database triggers reject update, delete and truncate. This protects ordinary
application writes; deployment database owners still control their database.
Audit metadata links back to source records and case history rather than copying
free-text notes, resolution evidence and financial payloads into another store.

`GET /api/audit/{type}/{id}` reads within the authenticated tenant. Types are
`TRANSACTION`, `PROVIDER_TRANSACTION`, `RECONCILIATION` and `CASE`. The newest
recorded entries are returned first, with a default limit of 50 and maximum of
100. Set `before` to the last returned sequence to read older entries. Ordering
reflects audit receipt, while `occurredAt` retains source-event chronology.

The integration suite verifies redelivery idempotency, actor and event metadata,
tenant isolation, cursor bounds, historical event compatibility and rejection of
all three database mutation operations.
