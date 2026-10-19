# Tenant-scoped investigation knowledge

The `ai` profile enables RAG in the existing read-only investigation tools.
Set `AI_MODEL`, `AI_EMBEDDING_MODEL`, `AI_API_KEY`, and `TYPESAFE_API_KEY`.
The embedding model must support 1536 dimensions; the application requests that
dimension explicitly. Model names remain deployment configuration. Base URLs
use the same optional overrides documented for investigations.

PostgreSQL needs pgvector installed. Compose and Testcontainers use
`pgvector/pgvector:0.8.7-pg18-bookworm`; Flyway creates the extension and AI schema.
The PostgreSQL major version and data volume location remain unchanged. For an
existing external database, install pgvector before applying migrations.

An authenticated administrator imports plain text with
`POST /api/knowledge/documents`:

```json
{
  "kind": "POLICY",
  "title": "Fee review policy — demo",
  "content": "A reported processing fee can explain a gross/net difference. An analyst must verify settlement evidence and document the conclusion before resolving a case."
}
```

Kinds are `POLICY`, `PROVIDER_DOCUMENTATION`, and `PAST_CASE`. The latter accepts
curated historical case notes as supporting context. Imports are limited to
20000 characters and ten passages, with an authenticated tenant/actor. Identical
imports return the original document and do not duplicate audit entries. Changed
content creates a new immutable document version; no overwrite/delete endpoint
is exposed. Embedding or database failures roll back the entire import.

Read original sources with `GET /api/knowledge/documents/{id}` and exact cited
passages with `GET /api/knowledge/passages/{id}`. Both enforce the authenticated
tenant. Recommendations cite `document-chunk:{id}`, preserving the passage used
at investigation time even after a newer document is imported.

The modular Spring AI retrieval pipeline rewrites and expands the query, performs
bounded pgvector retrieval with server-created tenant and document-kind filters,
then screens and reranks passages with TypeSafe Jev. Rewritten queries cannot
supply filter metadata. Retrieved content is reread from immutable tenant-scoped
sources before screening. Stored judgment metadata is discarded; unclassified
or unscored passages are withheld when the screening/ranking service fails.
Prompt injection is excluded; contradicting evidence remains explicitly labeled
`CONFLICTING`. Up to three passages reach each read-only search tool.

Similarity and judgment scores indicate retrieval support, not financial
correctness. The final recommendation still passes structured validation,
citation checks and the existing bounded judgment/refinement workflow. The AI
cannot import documents, change rules, or resolve cases. Analysts retain the
normal authorized resolution workflow.

Tests use real PostgreSQL/pgvector and actual model/judgment HTTP clients against
local protocol fixtures. They cover tenant and kind isolation, immutable passage
reads, idempotent imports, audit, rollback, screening/ranking outages, partial
judgments, prompt injection, contradictions, and a case investigation citing a
retrieved policy. They do not establish live model or retrieval quality.
