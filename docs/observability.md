# Observing reconciliation workflows

ReconFlow provides process health, Prometheus metrics, structured workflow logs,
and OpenTelemetry traces. The operator surfaces are separate from the tenant UI
and business APIs.

## Start the local demonstration

```sh
docker compose --profile observability up -d
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo,observability
```

The existing `ai` profile can also be included when AI services are configured.
The default application profile does not expose a management HTTP listener or
export telemetry. The opt-in `observability` profile provides:

| Surface | Local address |
| --- | --- |
| Tenant workspace/API | `http://localhost:8080` |
| Process liveness | `http://127.0.0.1:9091/actuator/health/liveness` |
| Dependency readiness | `http://127.0.0.1:9091/actuator/health/readiness` |
| Prometheus scrape | `http://127.0.0.1:9091/actuator/prometheus` |
| Jaeger trace viewer | `http://localhost:16686` |
| OTLP HTTP trace ingestion | `http://localhost:4318/v1/traces` |

Management binds to `127.0.0.1`, with only read-only health and Prometheus
endpoints exposed. Local host access is the operator boundary; it does not use
tenant-admin credentials. Do not forward the management listener through the
public application proxy. Jaeger's exposed ports also bind to loopback.
Business APIs still require bearer authentication on either listener.
Health responses contain only status, with no database or broker details.

`MANAGEMENT_PORT` changes the local management port.
`OTEL_EXPORTER_OTLP_TRACES_ENDPOINT` selects an alternate OTLP HTTP endpoint.
The local profile samples all traces for review; tune
`management.tracing.sampling.probability` for a hosted installation. The default
Jaeger all-in-one uses transient in-memory trace storage. Restarting it clears
telemetry, while financial records and audit history remain in PostgreSQL.

## Health semantics

Liveness checks whether the process is running. It intentionally excludes
external dependencies so an unavailable broker does not cause restart loops.
Readiness includes application readiness, PostgreSQL and provider Kafka.
The broker probe reuses Kafka's configured connection/security properties and
bounds metadata lookup and client shutdown. Kafka disabled in application
configuration is reported as UNKNOWN and is not treated as a failing dependency.

```sh
curl -f http://127.0.0.1:9091/actuator/health/readiness
curl -f http://127.0.0.1:9091/actuator/health/liveness
curl -f http://127.0.0.1:9091/actuator/prometheus
```

Stopping Kafka changes readiness to DOWN (HTTP 503); liveness stays UP.
Restart Kafka before generating further demo activity.

## Metrics

Useful metric families include:

- `http_server_requests_seconds`: request count, latency and status by route.
- `spring_kafka_template_seconds` / `spring_kafka_listener_seconds`: producer and
  consumer observations, including failures.
- `module_events_published_total`: domain event publication counts by module/type.
- Individual `module_events_published_*` event counters.
- Standard JVM, process and datasource pool metrics.

Metrics are process-local operational measurements, not financial/accounting
records. Event publication counters can include retried work and reset on
restart. The tenant dashboard uses persisted business data for its totals.
Business/tenant/resource IDs are not added as metric labels. Trace exemplars may
link measurements to individual traces without becoming metric dimensions.

## Traces and logs

Spring MVC, Kafka producer/consumer and Spring Modulith API/event-listener
observations produce spans. W3C trace
context propagates through Kafka headers and asynchronous task execution,
including the bounded investigation executor. In Jaeger, select service
`reconflow`, run a query and inspect `module.identifier` tags to follow the work
through provider ingestion, reconciliation, case creation and audit recording.

Structured ECS console logs include trace/span IDs. Workflow entries are emitted
only after a newly inserted audit record's transaction commits. They include
`workflow.action`, tenant/resource/event IDs and business correlation/causation
IDs. Payloads, financial amounts, tokens, notes and model prompts are not added
to these workflow log entries. Restrict operational log access to operators.
The append-only database audit history remains the authoritative business record.

A live delivery can share one trace across HTTP, Kafka and asynchronous module
listeners. Internal event replay after a process restart can start a new trace;
use stable event, business correlation and causation IDs to join the history.
Telemetry delivery is independent of durable business-event delivery.

## Reproducible verification

After startup, run:

```sh
python3 dev/observe.py --log-file /path/to/application.log
```

The check signs in with local demo credentials, creates an amount-mismatch case,
and supplies a sampled W3C trace parent to the provider simulator. It verifies:

1. Safe liveness/readiness responses and separation of operator/business routes.
2. A real exported Jaeger trace containing Kafka producer and consumer spans and
   provider, reconciliation, case and audit module spans under the same trace ID.
3. HTTP, Kafka, module and JVM Prometheus metrics without business ID labels.
4. A committed case-opening log correlated to that trace when a log file is given.

It prints the trace/case/reconciliation IDs for inspection in Jaeger and the UI.
Use `--base-url` when the application runs on another local port.
The probe uses the seeded local realm; it is a local demo check.

To demonstrate the health distinction after running the trace check:

```sh
docker compose stop kafka
python3 dev/observe.py --expect-broker-down
docker compose start kafka
```

CI runs this verification against the packaged application, actual Compose
services and Jaeger, after the eight standard demo scenarios and real-browser
workflow checks. It also confirms the broker-down readiness/liveness distinction.
