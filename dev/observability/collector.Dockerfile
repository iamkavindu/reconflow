FROM otel/opentelemetry-collector-contrib:0.137.0 AS collector
FROM alpine:3.22
COPY --from=collector /otelcol-contrib /otelcol-contrib
USER 65532:65532
ENTRYPOINT ["/otelcol-contrib"]
