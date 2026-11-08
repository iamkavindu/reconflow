# Local indexed observability

ReconFlow exports all three signals through one path:

```text
Host JVM -> Collector -> Data Prepper -> OpenSearch -> OpenSearch Dashboards
```

PostgreSQL remains the authoritative financial and business audit store. Telemetry
is operational evidence with bounded, lossy delivery. It is never a dependency
of application readiness or reconciliation. A backend outage can fill in-memory
queues, expire retries and lose telemetry; restarting a collector loses its queue.
Kafka and Modulith's durable business publications have different guarantees.

## Versions and compatibility

Compose pins OpenSearch **3.3.2**, Dashboards **3.3.0**, Data Prepper **2.12.2**,
and Collector contrib **0.137.0**. The Collector image adds Alpine **3.22** solely
for a real HTTP healthcheck. The one-shot schema initializer uses Python
**3.13.7-alpine**. The application uses Boot **4.1.1**, Micrometer OTLP **1.17.1**,
OpenTelemetry **1.62.0** and Logback appender **2.28.1-alpha** (matching Boot's
instrumentation BOM). No agent or second SDK is installed.

This implements classic **Trace Analytics**, **Metric analytics / Otel-Index**,
and saved Discover searches and dashboard visualizations. OpenSearch 3.3 explicitly
supports an indexed OTel metrics source in its [Metric analytics documentation](https://docs.opensearch.org/3.3/observing-your-data/prometheusmetrics/).
Dashboards 3.3.0 accepts this engine patch release according to its [pinned version compatibility check](https://github.com/opensearch-project/OpenSearch-Dashboards/blob/3.3.0/src/core/server/opensearch/version_check/opensearch_opensearch_dashboards_version_compatability.ts); a Dashboards 3.3.2 image was not published. It is not the newer APM setup that requires a separate metrics server.
The [Data Prepper 2.12.2 source](https://github.com/opensearch-project/data-prepper/tree/2.12.2)
provides the vendored mappings and plugin configuration. See also the official
[Logback appender instructions](https://github.com/open-telemetry/opentelemetry-java-instrumentation/tree/v2.28.1/instrumentation/logback/logback-appender-1.0/library).

Boot's resolved `OtlpMetricsPropertiesConfigAdapter.resourceAttributes()` applies
`OpenTelemetryResourceAttributes`, so the configured `service.name=reconflow`
reaches Micrometer as well as SDK traces and logs in this version. An older Boot
release may require different configuration; do not copy these properties blindly.

## Prepare and start

Install Java 25, Docker/Compose and Python 3.10+. Allow several GB of Docker RAM
and disk space for the pinned images. OpenSearch's heap is 512 MiB, Data Prepper's
heap 512 MiB, Dashboards' Node heap 512 MiB; container limits total about 4 GiB
before PostgreSQL, Kafka, Keycloak and the host JVM. Resource pressure can make
startup significantly slower.

Check the Linux host setting (Docker Desktop users check its Linux VM):

```sh
cat /proc/sys/vm/max_map_count
```

If below 262144, an administrator must deliberately set it, for example
`sudo sysctl -w vm.max_map_count=262144`. This repository does not change host
settings on local startup. CI explicitly prepares its disposable runner.

All telemetry services use plaintext **without authentication** in this local
profile. There are no telemetry passwords to provision. Published ports bind
only to loopback; ingestion ports on Data Prepper are internal to Compose.
Do not expose this local stack through a tunnel or public reverse proxy. The
existing demo realm credentials are for the business application, not OpenSearch.
Supply real application/AI secrets through the environment; do not commit them.

```sh
docker compose --profile observability up -d --build --wait --wait-timeout 300
python3 dev/observability/bootstrap.py
# Safe to repeat; stable object IDs are overwritten and telemetry is retained.
python3 dev/observability/bootstrap.py
LOCAL_AI_ENABLED=false ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

For the non-AI demo configuration, use `-Dspring-boot.run.profiles=demo,observability`.
The `local` profile includes `observability`. The base/demo profile alone disables
all exports and the management listener; normal console logging still works.
To run the packaged JAR, first `./mvnw package`, then:

```sh
LOCAL_AI_ENABLED=false java -jar target/reconflow-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

The Compose schema initializer must complete successfully before Data Prepper
starts. It installs explicit mappings before the first document can create an
incompatible dynamic mapping. The host bootstrap repeats schema application and
imports saved objects with stable IDs. It has bounded HTTP requests and waits,
reports import failures, and never deletes existing telemetry. This provisions
new indexes; it cannot repair an incompatible existing index in place.

| Surface | Address |
| --- | --- |
| Workspace / authenticated business API | http://localhost:8080 |
| Health / readiness / liveness | http://127.0.0.1:9091/actuator/health (append `/readiness` or `/liveness`) |
| OTLP HTTP/protobuf | http://127.0.0.1:4318/v1/traces, `/v1/metrics`, `/v1/logs` |
| OpenSearch query/admin | http://127.0.0.1:9200 |
| Dashboards | http://127.0.0.1:5601 |
| Operations dashboard | http://127.0.0.1:5601/app/dashboards#/view/reconflow-operations |

Only health is exposed on the operator listener. Business routes still require
bearer authentication; environment and individual Actuator meter endpoints are
not exposed. `MANAGEMENT_PORT` overrides the loopback management port.

`OTLP_BASE_URL` defaults to `http://localhost:4318` (no signal suffix). Independently
override complete URLs with `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT`,
`OTEL_EXPORTER_OTLP_METRICS_ENDPOINT` and `OTEL_EXPORTER_OTLP_LOGS_ENDPOINT`.
The documented trace override is preserved. The partial `OTLP_COLLECTOR` variable
is replaced by `OTLP_BASE_URL`; a complete trace URL is not a shared base URL.
Sampling is explicitly 100% locally. Metrics export every 10 seconds.

## Inspect the signals

Open the ReconFlow operations dashboard after running the probe. Saved searches
show workflow and error logs, spans and the actual metric documents. In Discover,
choose `reconflow-logs-*` (trace IDs link directly to the native waterfall) and filter `traceId:"<traceId from probe>"`. Workflow
columns retain action, case/resource, event, correlation and span IDs. Trace
Analytics supplies trace search and waterfall inspection using the
`otel-v1-apm-span-*` indexes. Search the same trace ID there. The probe supplies an external sampled parent; its upstream client span is intentionally absent, so that particular trace can have no root trace group. Data Prepper flushes spans after five seconds rather than retaining them for the default three minutes. Ordinary requests without an upstream parent supply complete local roots. The standard service-map pipeline is installed. No interservice edges were
observed for this single service, so its map may be empty; the waterfall shows
`reconflow`. Module identifiers remain span attributes.

In classic Observability > Metrics, select **OpenTelemetry** under Metrics source, then choose the current
`ss4o_metrics-otel-reconflow-v2-<UTC date>` under **Otel Index**. This release
lists histogram/timer names there; gauges and sum counters are available in the
operations dashboard and Discover. It does not offer a native gauge/counter
picker. Metrics use `ss4o_metrics-otel-reconflow-v2-*`, the
prefix used by the indexed metrics experience. The metric `name` is a keyword (with a compatibility `name.keyword` subfield); the native picker aggregates `name` directly. If earlier experimental indexes exist, they are preserved; select the v2 dataset. The source represents metric dimensions under `attributes` and converts dots in attribute names to `@`; logs use `severityText` and `log.attributes.workflow@action`, while spans use `span.attributes.module@identifier`. Saved visualizations also query
these documents directly; there is no external metrics data source.

The exporter was inspected with real HTTP/Kafka workflow traffic: metric names
are `jvm.memory.used`, `process.cpu.usage`, `http.server.requests`,
`spring.kafka.template`, `spring.kafka.listener`, `module.events.published` and
per-event `module.events.published.<module>.<Event>`. JVM memory units are
**bytes**, timer units are **seconds**, not Prometheus family names or assumed
UCUM aliases. Inspect `name`, `kind`, `unit`, `value`, `count`, `sum`,
`aggregationTemporality`, `time`, `startTime`, `attributes`, `bucketCountsList`
and `explicitBounds` in the metric saved search.

Counters/timers explicitly export **delta** windows. Count charts sum exported
interval counts in 10-second buckets; they are labelled counts, not rates.
Mean HTTP latency divides summed durations by summed counts in each bucket.
Restarting a process starts new delta windows, rather than requiring a derivative
of cumulative counters. Missing exports remain gaps and cannot be reconstructed;
counts are not authoritative business totals. Gauges show sample averages (the JVM panel is the average pool usage, not total heap).
Histograms are enabled for HTTP and Kafka timers and preserved by Data Prepper.
Data Prepper retains the histogram buckets; exponential histogram scales above 10 are outside this configuration. No percentile chart is claimed. Never average per-series percentiles or present
cumulative totals as per-second rates. Business references and resource IDs are
log/trace correlation fields, not metric dimensions.

Logs use Spring's SDK via the Logback appender. Exactly one console appender and
one OTLP appender attach in observability mode. ECS console output preserves
trace/span IDs and structured workflow fields. Logback captures SLF4J key-value
pairs without capturing arbitrary MDC. Committed workflow logging still happens
after the audit transaction commits. Amounts, financial payloads, access tokens,
case notes and model prompts are not added to telemetry by this integration.

## Verify and collect evidence

```sh
python3 dev/observe.py --base-url http://localhost:8080 > /tmp/reconflow-telemetry.json
# Optional additional console check:
python3 dev/observe.py --log-file /path/to/application.log
curl -fsS 'http://127.0.0.1:9200/_cat/indices?v'
curl -fsS 'http://127.0.0.1:9200/_cluster/health'
docker compose --profile observability ps -a
docker compose --profile observability logs --tail=100 otel-collector data-prepper opensearch dashboards
```

The probe authenticates, records an internal transaction, supplies a known sampled
W3C parent to a mismatching provider event, and waits for reconciliation and case
creation. It polls indexes for this trace, HTTP/Kafka/module spans, a committed
CASE_OPENED log matching database audit event/correlation IDs, and metrics newer
than this run. It checks resource identity, types, units, histogram content and
absence of business dimensions. JSON output identifies the run and per-signal
results. Optional console verification supplements indexed log verification.

To test business operation through an outage, keep the app running:

```sh
docker compose stop otel-collector data-prepper
python3 dev/observe.py --workflow-only
docker compose start data-prepper otel-collector
# Separate dependency-health test; recover Kafka before more demo activity.
docker compose stop kafka
python3 dev/observe.py --expect-broker-down
docker compose start kafka
```

The workflow-only mode explicitly reports that telemetry was not checked.
Normal probe mode never accepts a console log as indexed evidence. CI runs the
probe, the outage workflow, repeat bootstrap, and a normal OpenSearch restart
followed by querying the earlier trace ID. It retains JSON and component diagnostics.

## Retention, shutdown and troubleshooting

Stop the host app, then `docker compose --profile observability down`. Named
OpenSearch storage survives normal stop/down/restart. Never add `-v` to preserve
history. Logs/metrics use daily indexes; traces use daily rollover. Local ISM
policies retain telemetry for seven days, with background scheduling delays.
Service maps are operational relationships, not a second business audit store.
The bootstrap does not erase data on repeat runs.

* Startup: inspect `ps -a` and component logs, host map count, free disk and memory.
  A schema initializer failure intentionally prevents ingestion. Slow image
  downloads require completing the pulls before applying the bounded startup wait.
* Authentication/TLS: this profile consistently disables telemetry security and
  uses HTTP/insecure gRPC. An HTTPS URL or credentials from another cluster will
  not work here. Business authentication failures belong to Keycloak/session checks.
* Rejected exports: verify the three full HTTP paths, Collector receiver binding,
  internal gRPC ports 21890/21891/21892 and Data Prepper logs. Collector retries
  are bounded to 60 seconds with queues of 128 requests per signal.
* Mapping errors: compare `_index_template/reconflow-*` and actual index mappings.
  Templates only affect new indexes. Preserve old evidence; use an isolated
  Compose project or deliberate reindexing, rather than deleting local volumes.
* Missing logs: confirm the observability profile, OTLP appender installation,
  `/v1/logs` and `reconflow-logs-*`. Compare ECS console trace ID with indexed
  `traceId`; the audit log appears only after commit. Do not enable payload logging.
* Missing metrics: allow multiple 10-second export intervals, query fresh `time`
  values and dot-separated metric names. Generate actual HTTP/Kafka traffic.
  Inspect histogram count/sum/buckets and service resource identity before blaming
  the UI. A zero idle interval is legitimate.
* Missing dashboards: rerun bootstrap and inspect its saved-object import response;
  use the last 15 minutes and refresh the dashboard after running the probe.

Runtime verification status and any environment limitations are recorded in the
acceptance guide. Configuration and a successful build alone are not proof that
the complete indexed pipeline or browser rendering passed.
