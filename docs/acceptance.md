# ReconFlow v1.0 acceptance review

This review maps the agreed **Product Definition & MVP Specification v0.1**
(functional requirements, non-functional requirements, acceptance scenarios,
feature boundary and definition of done) and **Core Workflow Refinement & Domain
Model v0.1** to the implementation. It does not expand their MVP boundary.

The implementation is a release candidate. Automated checks cover the business
workflows and AI integration contracts. Live model, embedding and TypeSafe service
quality still requires a configured run and human review before claiming full
portfolio acceptance. No live-service pass is implied by CI fixture results.

## Requirement evidence

Paths below are repository files. The tests and scripts are executable checks;
documentation alone is not evidence that a live service passed.

| Agreed requirement | Implementation / evidence |
| --- | --- |
| Authenticate, operate within a tenant, ADMIN/ANALYST/VIEWER roles (§16.1–2) | `tenancy`; `AuthenticatedApiIntegrationTest`, `KeycloakRealmIntegrationTest`; real Keycloak browser sign-in |
| Submit/view internal payments and refunds (§16.3,5) | `transactions`; `TransactionRecordingIntegrationTest`, `MoneyTest`; demo runner and browser source entry/search |
| Simulate/view provider events (§16.4) | `provider`; `ProviderSimulationIntegrationTest`, `ProviderEventKafkaIntegrationTest`; real Compose Kafka delivery |
| Observe results, discrepancies and cases (§16.6–9) | `reconciliation`, `cases`; `AutomaticReconciliationIntegrationTest`, `DiscrepancyIntegrationTest`, `MatchingWindowExpiryIntegrationTest`; eight demo scenarios |
| Investigate and resolve through permitted transitions (§16.10,13) | `CaseManagementIntegrationTest`, `CaseTest`, authenticated API checks; explicit browser review/note/resolution |
| AI findings, read-only tools, RAG and evidence (§16.11–12) | `AiInvestigationIntegrationTest`, `TenantKnowledgeIntegrationTest`, `AiReadOnlyArchitectureTest`; packaged AI-enabled browser check with local protocol fixtures |
| Audit important actions (§16.14) | `AuditHistoryIntegrationTest`, `InvestigationLifecycleIntegrationTest`; demo audit assertions; browser resolution audit |
| Basic dashboard and mandatory ID/reference/status/date search (§16.15,18) | `OperationalSearchIntegrationTest`; `dev/demo.py` and `dev/browser/operations.spec.js` |
| Idempotency and retained financial history (§19; domain invariants) | Duplicate delivery tests; demo replay compares original identities and dashboard counts; resolution preserves determination |
| Tenant isolation including AI retrieval (§11,19,21.E) | Signed-token API tests; pgvector tenant/kind tests; demo checks both tenant directions and foreign investigation/citation denial |
| Reliable asynchronous delivery (§19) | Kafka retry/order/DLT tests; `EventRecoveryIntegrationTest` restarts the application over the same database with AI disabled and verifies an unfinished publication completes |
| Useful health, logs, metrics and traces (§19) | `dev/observe.py`: indexed OpenSearch HTTP/Kafka/module trace, correlated committed audit log, indexed OTLP metrics, safe operator endpoints and broker-down readiness with process liveness UP |
| Testability/evolvability (§19) | Real PostgreSQL/pgvector, Kafka and Keycloak; module-boundary test; [architecture](architecture.md) explains ownership and recovery |
| Minimal analyst/admin UI, API docs, reproducible data (§26–27) | Static workspace, authenticated OpenAPI, README startup, demo runner, three browser checks across the ordinary and AI-enabled configurations |

Candidate domain values or future capabilities are not additional mandatory
features. Settlement remains supporting evidence; assignment queues, configurable
workflow/rule builders and automated accounting stay outside v1.0.

## Acceptance scenarios A–E

| Scenario | Automated demonstration | Remaining live acceptance |
| --- | --- | --- |
| A: USD 100 successful match, no case | `perfect_match` in `dev/demo.py` | None beyond reviewing the documented local startup |
| B: USD 100 / USD 96.80, fee USD 3.20, AI evidence, human resolution, audit | `provider_fee`; real HTTP adapters and pgvector with fixture AI services; AI-enabled Chromium case request/citation read/manual resolution and retained REVIEW_REQUIRED result | Run with live configured services; assess actual fee explanation, citations, limitations and human decision |
| C: repeated provider event, one financial effect | `duplicate_webhook`, same-run replay and Kafka tests | None |
| D: absent provider, UNMATCHED after window, case | `missing_provider` and expiry deadline tests; reverse orphan scenario also included | None |
| E: tenant isolation in APIs and AI | Two-way demo API denial; AI source/citation denial; tenant-filtered retrieval integration tests | Confirm the configured live run also reports tenant isolation |

## Start and verify from a clean checkout

Prerequisites: Java 25, Docker with Compose, Python 3.10+; Node 20+ is needed only
for browser development checks. The Maven wrapper downloads Maven. Local ports
5432, 8180, 9092 and 8080 must be available; observability also uses 9091, 4318
and 9200/5601.

```sh
docker compose --profile observability up -d --build --wait --wait-timeout 300
python3 dev/observability/bootstrap.py
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo,observability
```

In another terminal:

```sh
python3 dev/demo.py > demo-result.json
python3 dev/observe.py
```

The demo waits for real authentication/API availability, creates the eight
scenarios, waits for the 30-second demo arrival window, checks audit history and
both tenant boundaries, and prints resource IDs. The observation probe creates
one additional mismatch case. Counts are cumulative; fresh-database CI checks the
initial expected totals before browser/observation activity adds further records.
See [demo](demo.md) for replay and [observability](observability.md) for log and
broker-down checks.

Open `http://localhost:8080`, sign in as `analyst` / `demo-password`, inspect the
fee case and evidence, start review, and record an explicit human resolution.
Load the case audit history and confirm the reconciliation remains
REVIEW_REQUIRED. The base demo intentionally reports AI as unconfigured.

`docker compose --profile observability down` stops services while preserving
database/Kafka/OpenSearch volumes. Subsequent runs retain prior evidence; choose a fresh run
ID for another demonstration. Keycloak reimports the seeded local realm on start.
Do not run two application instances against the demo database while following
these single-process acceptance instructions.

## Live AI acceptance — not yet verified

Use a dedicated local demo database and genuine service credentials supplied
through your local environment. Do not reuse the fixed-vector CI fixture database
to evaluate live retrieval. Configure:

| Variable | Required capability |
| --- | --- |
| `AI_MODEL` | Compatible chat model with tool calling and structured JSON support |
| `AI_EMBEDDING_MODEL` | Model supporting explicitly requested 1536-dimensional embeddings |
| `AI_API_KEY` | Credential for configured chat/embedding endpoint |
| `TYPESAFE_API_KEY` | Credential for TypeSafe Jev judgment/screening/ranking |
| `AI_BASE_URL`, `TYPESAFE_BASE_URL` | Optional compatible endpoint overrides |

No credentials are included in the repository or normal CI. Stop the non-AI
application first, then run:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
python3 dev/demo.py --investigate > live-ai-result.json
```

See [local development](local-development.md) for the central local YAML,
environment settings and profile responsibilities.

The runner imports the review policy and simulated provider guide. It requires a
completed, accepted recommendation with a readable documentation citation, checks
investigation lifecycle audit and foreign-tenant denial, and compares the case,
reconciliation, internal/provider records and settlement evidence before/after.
It never resolves the case. A missing citation or failed judgment leaves the
attempt available for inspection and fails acceptance.

Before resolving, a human must check the actual output:

1. The recommendation distinguishes reported facts from inference and identifies
   the possible USD 3.20 fee explanation without inventing a contractual tariff.
2. Cited financial records and original documentation support the explanation;
   limitations and uncertainty are useful. Fixture scores do not satisfy this.
3. The case is still unresolved. The analyst chooses a permitted resolution and
   reason, confirms the final decision, and verifies audit and retained financial
   determination through the UI.

Record the tested Git commit, actual model, prompt version, case/investigation IDs,
judgment results and human assessment. The investigation response retains this
metadata. Do not commit credentials or publish private service data. A live pass
can then be recorded in a follow-up acceptance update; it has not been performed
in the implementation environment, where these service settings are unavailable.

## What CI proves

The required `verify` job runs the Maven suite and restart recovery test, Compose
Kafka delivery, all eight scenarios and idempotent replay using `local` with
`LOCAL_AI_ENABLED=false`, two ordinary browser
checks and real exported telemetry. After waiting for application shutdown, it
launches the packaged application with the same `local` profile, points actual HTTP
clients at `dev/ai_fixture.py`, runs the strengthened investigation demo and the
AI-enabled browser check, and records JSON evidence/screenshots as artifacts.
The broker-down readiness/liveness check runs last, so broker recovery does not
consume a subsequent scenario's short demo matching window.

The fixture is loopback-only, external to the application JAR, and used only in
CI. Its vectors, recommendations and judge scores are fixed. It proves service
contract wiring, citation rendering, application authorization and human-control
boundaries, not probabilistic quality. Guardrail failure/refinement/outage cases
remain covered by the focused Java integration suites.

## Local observability migration verification — 2026-11-08

Executed against isolated Compose projects; existing user files and persistent
volumes were preserved. These are runtime results, not configuration-only claims:

| Check | Observed result |
| --- | --- |
| Complete Maven suite | 135 tests, zero failures/errors/skips; 1 minute 43 seconds with cached images |
| Exports disabled | Packaged demo startup, eight authenticated scenarios and tenant isolation passed; management listener absent |
| Exports enabled | Packaged local startup; independently indexed logs, traces and OTLP metrics passed |
| Fresh infrastructure | New OpenSearch volume, schema startup gate, fresh and repeat saved-object bootstrap passed |
| Workflow trace | `3e2c049cc51a33d433294a189cb51d59`; 15 spans, HTTP server, Kafka producer/consumer, provider/reconciliation/cases/audit |
| Committed case log | Case `f660e1df-eaac-4f9e-877b-cebaaf01dfa8`; event and business correlation IDs matched PostgreSQL audit; ECS console also matched |
| Metrics | Fresh JVM gauge, HTTP/Kafka histograms and module delta sums; actual units/values/buckets and no business dimensions verified |
| Application restart | A second fresh workflow passed delta metric and histogram assertions after process reset |
| Telemetry outage | Stopped Collector and Data Prepper; authenticated mismatch still reconciled and opened a case with readiness UP |
| Broker outage | Readiness DOWN, liveness UP; broker restored |
| Normal OpenSearch restart | Prior trace retained: 15 spans, 3 logs and 4293 fresh metric documents |
| Browser | Two existing authenticated browser checks passed; operations dashboard, 15-span waterfall and native indexed timer picker passed in installed Chrome |
| Startup timing | Final cached Compose startup including readiness checks: 12.74 seconds |

Evidence generated in this run: `/tmp/reconflow-fresh-telemetry.json`,
`/tmp/reconflow-fresh-restart-evidence.json`, fresh bootstrap JSON files,
`/tmp/reconflow-full-verify.log` and browser screenshots under
`dev/browser/screenshots/observability*.png`. CI generates equivalent telemetry,
bootstrap, restart JSON and screenshot artifacts. GitHub Actions itself was not
run remotely during this local session.

OpenSearch 3.3.2 and Dashboards 3.3.0 were verified together. The native metric
picker lists indexed histograms/timers; gauge and sum-counter views use the
provisioned operations dashboard and Discover. No external metrics database is
required. With one service, no interservice map edges/documents were observed;
the standard service-map pipeline is installed, and the waterfall identifies
`reconflow` without inventing module services. The supplied external trace parent
has no upstream client span, so that probe trace has no root trace-group name.

Seven-day ISM expiry was configured and the trace index's active custom rollover
policy was queried; seven days of wall-clock expiry were not waited out. Bounded
telemetry queues can lose data. Cold image downloads in this environment were
slow and initially interrupted verification; all required images eventually
completed and the complete suite was rerun successfully. CI has a 35-minute job
budget and bounded service/probe waits; cold registry delays remain external.

Validation used `reconflow-observe-check` for PostgreSQL/Kafka/Keycloak and
`reconflow-observe-fresh` for the final telemetry volume. Containers are stopped
after verification and both projects' named volumes remain. Ordinary documented
startup uses the default Compose project and creates its own local data. To
inspect the recorded run, start the three business services with
`docker compose -p reconflow-observe-check up -d postgres kafka keycloak`, then
start `dashboards otel-collector` with
`docker compose -p reconflow-observe-fresh --profile observability up -d --wait dashboards otel-collector`.
