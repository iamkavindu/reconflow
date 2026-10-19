# ReconFlow

AI-assisted payment reconciliation and exception management for a fictional
commerce company. The application compares immutable internal and provider
records, records deterministic discrepancies, and opens cases for human review.
AI investigates and proposes; authorized application workflows control resolution.

The backend is a Java 25 / Spring Boot modular monolith with PostgreSQL,
Spring Modulith's persistent event registry, Kafka provider ingestion, Keycloak
OIDC authentication, and optional Spring AI / pgvector / TypeSafe Jev investigations.

See the [architecture](docs/architecture.md) for module ownership, financial and
AI authority boundaries, and failure recovery. The [MVP acceptance review](docs/acceptance.md)
maps the agreed requirements to verification and documents the remaining live AI
quality check. CI's AI fixtures establish integration behavior, not live quality.

## Start locally

Install Java 25, Docker with Compose, and Python 3.10+. The Maven wrapper is included.
For SDKMAN users, `.sdkmanrc` provides the repository's Java version:

```sh
sdk env install
sdk env
docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Open `http://localhost:8080` and sign in through Keycloak. The local demo
accounts are `admin`, `analyst`, and `viewer`, with password `demo-password`.
See [the workspace guide](docs/analyst-ui.md) for browser/session behavior.

In another terminal:

```sh
python3 dev/demo.py
```

The runner authenticates against the local demo realm, creates all eight agreed
scenarios through the APIs, waits for their outcomes, checks audit history and
tenant isolation, and prints resource IDs and dashboard counts as JSON.
It never resolves cases or changes financial evidence.

| Service | Local address |
| --- | --- |
| API | `http://localhost:8080` |
| Keycloak | `http://localhost:8180` |
| Kafka | `localhost:9092` |
| PostgreSQL | `localhost:5432` |

The `demo` profile seeds two tenant memberships and uses a 30-second matching
window, with scans every five seconds. The normal application default remains
ten minutes. These local credentials and plaintext infrastructure are demo
configuration; supply deployment-specific identity and infrastructure settings
for a hosted environment.

## Demo scenarios

| Scenario | Expected result |
| --- | --- |
| Perfect match | MATCHED, no case |
| USD 100.00 / USD 96.80 plus USD 3.20 reported fee | REVIEW_REQUIRED, amount and fee discrepancies |
| Amount mismatch | REVIEW_REQUIRED |
| Currency mismatch | REVIEW_REQUIRED |
| Three deliveries of the same provider event | One provider record and one MATCHED determination |
| Missing provider payment | UNMATCHED after the window |
| Orphan provider payment | UNMATCHED after the window |
| Refund mismatch | REVIEW_REQUIRED |

Each run uses a fresh suffix on `ORD-1001` through `ORD-1008` so demonstrations
can coexist. Reuse `--run-id` to replay a completed run without duplicating its
financial sources. If an earlier partial run's window expired before its
counterparts arrived, choose a new run ID; historical determinations stay intact.

See [the demo guide](docs/demo.md) for tokens, optional AI investigation and human
resolution. The analyst/admin workspace exposes the operational workflows. See
[observability](docs/observability.md) for operator health, metrics, logs and a
reproducible trace through the asynchronous workflow.

## Verify

```sh
./mvnw --batch-mode --no-transfer-progress verify
```

Tests exercise real PostgreSQL/pgvector, Kafka and Keycloak through
Testcontainers. Model-client tests use local protocol fixtures rather than live
AI credentials. CI also checks the Compose broker and runs the complete demo
against the application with real local OIDC authentication. Browser checks cover
both AI-disabled behavior and an AI-enabled case investigation through cited
evidence and explicit human resolution, using local AI protocol fixtures.

## API and architecture guides

- [Authentication and API access](docs/authentication-api.md)
- [Operational search and dashboard](docs/operational-search.md)
- [Provider delivery and retries](docs/provider-delivery.md)
- [Automatic reconciliation](docs/automatic-reconciliation.md)
- [Cases and resolution](docs/exception-workflow.md)
- [Settlement evidence](docs/settlement-evidence.md)
- [Audit history](docs/audit-history.md)
- [AI investigations](docs/ai-investigations.md)
- [Tenant-scoped knowledge retrieval](docs/knowledge-retrieval.md)

OpenAPI is available at `/v3/api-docs` with a valid bearer token. Module-boundary
tests keep financial, case, identity, AI and reporting responsibilities explicit.
The application performs no real payment, payout or refund execution.
