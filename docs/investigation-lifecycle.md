# Case-owned investigations

Case Management owns the AI-assisted investigation record and lifecycle:
REQUESTED, RUNNING, COMPLETED or FAILED. A separate AI adapter produces advisory
results. Investigations do not edit the case status, resolve a case or rewrite
financial sources or deterministic reconciliation findings.

Only an analyst or administrator can request an investigation for an unresolved
case in their authenticated tenant. A database constraint permits one active
investigation per tenant/case, so repeated or concurrent requests return the same
active record. Failed attempts remain in the history and a later explicit request
creates another investigation. A request publishes a durable Modulith event; its
identity and correlation ID are the investigation ID.

The internal processing port supports starting, completing and failing the work.
It is not an HTTP write API and must never be registered as a model tool. It
receives an explicit trusted tenant for background work. Optimistic locking
protects concurrent updates; repeated delivery after a terminal result returns
the original result. Recommendations require an accepted judgment and retain the
model and prompt version. Worker state events retain the request event as their
causation ID and feed append-only audit history.

Structured results contain summary, likely cause, cited evidence, an optional
suggested human resolution, confidence, limitations, and judgment scores. Scores,
text lengths and collection sizes are bounded. The result and its evidence are
immutable. Confidence and a suggested resolution are advisory information, not
authorization to take a financial or case action.

This slice supplies the lifecycle ports and persistence. The following AI
execution slice wires HTTP requests, bounded read-only tools and generation to
these ports. Integration tests cover concurrent request deduplication, immutable
completion, failed-attempt history, role and tenant checks, resolved-case
protection and audit delivery.
