# Run locally with AI configured

Use the `local` Spring profile for the complete local environment:

```sh
docker compose --profile observability up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Configure the AI environment variables below before starting the application.
This runs the real application and configured external AI services. CI verifies
the same profile against explicitly configured local protocol fixtures; a live
model quality pass is still pending.

## Configuration files and their responsibilities

| File | Purpose |
| --- | --- |
| `application.yaml` | Shared application defaults and processing behavior; AI and management HTTP are disabled by default |
| `application-local.yaml` | Local PostgreSQL, Kafka, OIDC, application listener, matching window and AI configuration |
| `application-demo.yaml` | Standalone credential-free demonstration preset with local infrastructure, seeded memberships and a short matching window |
| `application-ai.yaml` | Reusable AI capability preset for an explicitly configured environment, including the existing `demo,ai` command |
| `application-observability.yaml` | Shared operator listener, metrics, logging and tracing preset |

`local` automatically activates `observability` through the profile group declared
in `application.yaml`. It enables AI directly in the local file; you do not need
to select `ai`, `demo` or `observability` separately. AI beans are controlled by
their configuration properties. The optional `ai` profile remains available for
existing commands and other explicitly configured environments.

The local file is the starting point for local connections and AI settings.
Common framework defaults stay in the base file; operator telemetry settings
stay in the shared observability preset. Environment placeholders allow machine
settings and credentials to change without editing tracked YAML.

No staging/production preset is provided because a hosted deployment has not
been specified. The local infrastructure credentials and identity seeds are for
this local environment.

## 1. Prerequisites and AI configuration

Install Java 25, Docker with Compose and Python 3.10+. The Maven wrapper is
included. SDKMAN users can run `sdk env install` followed by `sdk env`.

In the terminal that will start the application, set:

```sh
export AI_MODEL='<your compatible chat model>'
export AI_EMBEDDING_MODEL='<your 1536-dimension embedding model>'
read -rsp 'Model API key: ' AI_API_KEY
printf '\n'
export AI_API_KEY
read -rsp 'TypeSafe API key: ' TYPESAFE_API_KEY
printf '\n'
export TYPESAFE_API_KEY
```

These prompts are Bash commands. Model names above are placeholders: the chat
model must support tool calls and structured JSON, and the embedding model must
support the explicitly requested 1536 dimensions. The application uses the same
model API credential/endpoint for chat and embeddings. TypeSafe Jev supplies
judgment, screening and ranking. Keys remain in the process environment.

For live GPT-5 nano investigations, use `AI_REQUEST_TIMEOUT=PT120S` in the
IntelliJ Run/Debug environment and restart the application. This is the validated
two-minute maximum **per model request**, not the duration of an investigation.
Keep retries disabled while measuring repeated live runs. Record elapsed time,
failure category and workflow stage; one successful run does not establish a
timeout-free service. TypeSafe has a separate `TYPESAFE_REQUEST_TIMEOUT` setting
(default `PT60S`, maximum `PT120S`) for judgment, screening and ranking.

Use `python3 dev/demo.py --investigate --investigation-timeout 1800` for live
acceptance. The investigation may perform up to eight model rounds, plus bounded
retrieval and judgment calls. This polling allowance is independent of individual
request deadlines and may need adjustment for observed dependency latency.
The default 300-second polling allowance is suitable for the protocol fixtures.

The application retrieves screened policy and provider passages before generation.
When any such passage is supplied, a recommendation must cite an exact supplied
documentation reference before judgment. A missing citation consumes the same
single refinement allowance as other validation or judgment failures. Empty
retrieval permits an advisory result with an explicit limitation; the seeded demo
still requires a readable documentation citation for its stricter acceptance check.

Failure logs include investigation ID, workflow stage, error category, exception
and root-cause classes, elapsed milliseconds and model. `request_timeout` records
the configured generation or judgment deadline; retrieval/tool stages report
`dependency-specific` because they may invoke several separately timed clients.
API failure messages remain generic. Logs omit provider messages, payloads and
credentials. Retry policy changes require accounting for latency, cost and repeated
tool execution; this fix does not introduce retries.

| Setting | Default / meaning |
| --- | --- |
| `DB_URL` | `jdbc:postgresql://localhost:5432/reconflow` |
| `DB_USERNAME`, `DB_PASSWORD` | Both `reconflow`, matching Compose |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` |
| `OIDC_ISSUER` | `http://localhost:8180/realms/reconflow` |
| `OIDC_JWK_SET_URI` | `http://localhost:8180/realms/reconflow/protocol/openid-connect/certs` |
| `SERVER_PORT` | `8080` |
| `LOCAL_MATCHING_WINDOW` | `PT30S` for practical local investigation; normal base default remains ten minutes |
| `LOCAL_AI_ENABLED` | `true`; set `false` to use the same local infrastructure with AI disabled |
| `AI_MODEL`, `AI_EMBEDDING_MODEL` | Required when local AI is enabled |
| `AI_API_KEY`, `TYPESAFE_API_KEY` | Required when local AI is enabled |
| `AI_BASE_URL` | `https://api.openai.com/v1`; optional compatible endpoint override |
| `AI_REQUEST_TIMEOUT` | `PT60S` per chat request; must be positive and at most `PT2M` |
| `AI_MAX_COMPLETION_TOKENS` | `8192` per chat request, including reasoning and visible output; integer from 1 to 32768 |
| `TYPESAFE_BASE_URL` | `https://api.typesafe.ai`; optional compatible endpoint override |
| `MANAGEMENT_PORT` | `9091`, through the shared observability preset |
| `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` | `http://localhost:4318/v1/traces`, through the shared observability preset |

Changing an issuer also requires the appropriate JWKS URL and imported identities.
The seeded browser client allows application ports 8080 and 18080; register the
exact redirect/web origin before using another port. The repository does not
automatically load a `.env` file. Supply variables through the launching shell or
your IDE's run configuration. Credentials are not intended to be written into
the tracked YAML files.

## 2. Start infrastructure and application

Run the two startup commands at the top of this guide. Ports 5432, 9092, 8180,
8080, 9091, 4318 and 16686 must be available. Application and operator listeners
bind to loopback. Missing required AI settings fail startup when AI is enabled.

Flyway loads normal migrations and the idempotent local tenant/membership seed.
It does not automatically create payment scenarios, import policies or call the
models. Only one application instance should use this local setup at a time.

Open `http://localhost:8080` and sign in through Keycloak. Users `admin`,
`analyst`, `viewer`, and `other-analyst` use password `demo-password`; the last
belongs to the other tenant. PostgreSQL and Kafka retain data in Compose volumes.

## 3. Generate activity and observe the AI workflow

In another terminal, from the repository root:

```sh
python3 dev/demo.py --investigate > local-ai-result.json
```

The runner signs in, creates the eight scenarios through the real APIs and
Kafka, imports the review policy/provider guide, and investigates the USD 3.20
fee case. It verifies readable citations, investigation audit, tenant denial and
preservation of case/financial state. The printed result includes IDs, actual
model/prompt metadata, findings and an `aiAcceptance` check summary. AI requests
use the configured external endpoints and credentials; they are not fixture calls
unless you explicitly replace those endpoints.

In the workspace, refresh the overview, find the fee case using the result's
case ID, and inspect its investigation findings, limitations and cited passage.
You can also request another investigation through the case page. An analyst
must review the evidence, choose a resolution, supply a reason and confirm the
final decision. Load the case audit history afterward. The original reconciliation
remains REVIEW_REQUIRED. The runner never resolves the case automatically.

The administrator's Documentation screen supports additional policy/provider
text imports. A viewer can inspect evidence but cannot request or resolve work.
See [acceptance](acceptance.md) for the actual human quality assessment needed
before recording a live AI pass.

To exercise ten-minute arrival windows, set `LOCAL_MATCHING_WINDOW=PT10M` before
startup and use `python3 dev/demo.py --investigate --timeout 700`.

## 4. Health, traces and shutdown

Use `http://127.0.0.1:9091/actuator/health/readiness`,
`http://127.0.0.1:9091/actuator/health/liveness` and
`http://127.0.0.1:9091/actuator/prometheus` for operators. Jaeger is at
`http://localhost:16686`. Run `python3 dev/observe.py` for the trace/metrics probe;
it creates one additional case. See [observability](observability.md) for details.

Stop the application with Ctrl-C, then run
`docker compose --profile observability down`. Volumes and historical evidence
remain. Use a fresh demo run ID for another investigation after resolving a case.
