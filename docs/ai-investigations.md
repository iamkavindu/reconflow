# Advisory AI investigations

Analysts and administrators can request an investigation with
`POST /api/cases/{caseId}/investigations`. The response is `202 Accepted` with
the investigation location. Read it with `GET /api/investigations/{id}`, or
read the case's investigation history with `GET /api/cases/{caseId}/investigations`.
All requests require the existing tenant-scoped bearer authentication.

The [local environment](local-development.md) enables execution through the
single `local` profile. The separate `ai` capability profile remains available
for explicitly composed environments. Configure `AI_MODEL`, `AI_API_KEY`, and
`TYPESAFE_API_KEY`; optional `AI_BASE_URL` and `TYPESAFE_BASE_URL` select compatible
endpoints. Credentials belong in environment variables. With execution disabled,
requesting an investigation returns `503 Service Unavailable`.

Chat requests use a 60-second timeout by default. Set `AI_REQUEST_TIMEOUT` in
the application process environment to a positive ISO-8601 duration up to `PT2M`
to accommodate the configured model. Retries remain disabled. This is a timeout
for each model call, including retrieval query rewriting and expansion, rather
than the whole investigation. Embedding and TypeSafe requests retain their
separate timeouts. If the demo runner stops waiting while an investigation is
still running, increase its polling budget, for example
`python3 dev/demo.py --investigate --investigation-timeout 600`.

The worker gathers immutable financial evidence through public read ports and
exposes only case-bound read tools. Tenant and resource identities are fixed by
the application, never supplied by the model. The adapter uses bounded model and
tool calls, request timeouts, structured output validation, and exact evidence
reference checks. TypeSafe Jev judges groundedness, relevance, and advisory scope.
One refinement attempt is permitted. Unsupported output or a judgment outage
produces a retained FAILED attempt with a safe error message.

Completion stores the recommendation, evidence, limitations, model, prompt
version, and judgment scores. It does not change the case or reconciliation.
An analyst must use the normal validated resolution endpoint to resolve a case.
Lifecycle events remain auditable. Outstanding worker event publications can be
republished on restart when the AI profile is active.

The AI profile also enables tenant-scoped document retrieval. Configure
`AI_EMBEDDING_MODEL` with a model supporting 1536-dimensional embeddings and use
the provided pgvector PostgreSQL image. See [knowledge retrieval](knowledge-retrieval.md)
for document imports, citations and retrieval boundaries. Missing policy evidence
is stated as a limitation: a provider-reported fee does not establish the
contractual fee tariff.

Integration tests exercise the actual model and judgment HTTP clients against
local protocol fixtures and real PostgreSQL. They cover read tools, bounded
refinement, fabricated citations, unavailable write tools, rejected judgments,
outages, and preservation of financial and case state. These tests do not assess
a live model's recommendation quality or require external AI credentials.
