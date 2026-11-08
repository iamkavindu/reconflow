#!/usr/bin/env python3
"""Verify local operator health, metrics and an exported provider-to-case trace."""
import argparse
import json
import secrets
import time
import urllib.error
import urllib.request
from pathlib import Path
from demo import Api, DemoError, Login, identifier, now


def read(url, text=False):
    try:
        with urllib.request.urlopen(url, timeout=5) as response:
            body = response.read().decode()
            return response.status, body if text else json.loads(body)
    except urllib.error.HTTPError as error:
        return error.code, None
    except (OSError, urllib.error.URLError):
        return 0, None


def wait(read_value, description, timeout=60):
    deadline = time.monotonic() + timeout
    while True:
        value = read_value()
        if value:
            return value
        if time.monotonic() >= deadline:
            raise DemoError('Timed out waiting for ' + description)
        time.sleep(0.5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', default='http://localhost:8080')
    parser.add_argument('--management-url', default='http://127.0.0.1:9091')

    parser.add_argument('--opensearch-url', default='http://127.0.0.1:9200')
    parser.add_argument('--timeout', type=int, default=120)
    parser.add_argument('--log-file', type=Path)
    parser.add_argument('--workflow-only', action='store_true', help='Verify reconciliation during a deliberately stopped telemetry backend')
    parser.add_argument('--expect-broker-down', action='store_true')
    args = parser.parse_args()
    management = args.management_url.rstrip('/')
    if args.expect_broker_down:
        wait(lambda: read(management + '/actuator/health/readiness')[0] == 503, 'broker-down readiness')
        status, live = read(management + '/actuator/health/liveness')
        assert status == 200 and live == {'status': 'UP'}, 'Dependency failure must not fail process liveness'
        print(json.dumps({'readiness': 'DOWN', 'liveness': 'UP', 'brokerDownVerified': True}))
        return
    for group in ('liveness', 'readiness'):
        wait(lambda: read(management + '/actuator/health/' + group) == (200, {'status': 'UP'}), group)

    for url in (args.base_url + '/actuator/health', management + '/api/session', management + '/actuator/env', management + '/actuator/metrics'):
        assert read(url)[0] in (401, 403, 404), 'Operator/business listener separation: ' + url
    assert read(args.base_url + '/api/session')[0] == 401
    started = now()
    login = Login('http://localhost:8180/realms/reconflow', 'admin', 'demo-password')
    api = Api(args.base_url, login.token, login.refresh)
    run = secrets.token_hex(4)
    ref = 'OBS-' + run
    occurred = now()
    api.post('/api/transactions', {'businessReference': ref, 'type': 'PAYMENT', 'amount': 42,
                                  'currency': 'USD', 'occurredAt': occurred})
    trace_id = secrets.token_hex(16)
    source = {'providerEventId': 'evt-obs-' + run, 'providerTransactionReference': 'PAY-OBS-' + run,
              'businessReference': ref, 'type': 'PAYMENT', 'amount': 40, 'currency': 'USD',
              'status': 'SUCCEEDED', 'occurredAt': occurred}
    request = urllib.request.Request(args.base_url + '/api/provider-events/simulate', method='POST',
        data=json.dumps(source).encode(), headers={'Authorization': 'Bearer ' + api.token,
            'Content-Type': 'application/json', 'traceparent': '00-' + trace_id + '-' + secrets.token_hex(8) + '-01'})
    with urllib.request.urlopen(request, timeout=15) as response:
        assert response.status == 202
    def determined():
        records = api.find('/api/reconciliations', reference=ref)
        return records[0] if len(records) == 1 and records[0]['status'] == 'REVIEW_REQUIRED' else None
    reconciliation = wait(determined, 'probe reconciliation')
    rec_id = identifier(reconciliation, 'reconciliationId')
    case = wait(lambda: api.get('/api/reconciliations/' + rec_id + '/case', missing=True), 'probe case')
    case_id = identifier(case, 'caseId')
    if args.workflow_only:
        print(json.dumps({'traceId': trace_id, 'caseId': case_id, 'reconciliationId': rec_id, 'workflowVerified': True, 'telemetryChecked': False}))
        return

    def search(index, query):
        request = urllib.request.Request(args.opensearch_url.rstrip('/') + '/' + index + '/_search',
            method='POST', data=json.dumps({'size': 2000, 'query': query}).encode(),
            headers={'Content-Type': 'application/json'})
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                result = json.load(response)
                assert not result.get('timed_out') and not result['_shards']['failed'], result
                return [hit['_source'] for hit in result['hits']['hits']]
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return []
            raise

    def field(doc, name):
        if name in doc:
            return doc[name]
        value = doc
        for part in name.split('.'):
            value = value.get(part, {}) if isinstance(value, dict) else {}
        return value if value != {} else None

    trace_summary = {}
    def exported():
        spans = search('otel-v1-apm-span-*', {'term': {'traceId': trace_id}})
        modules = {field(span, 'span.attributes.module@identifier') for span in spans} - {None}
        kinds = {str(span.get('kind', '')).upper().removeprefix('SPAN_KIND_') for span in spans}
        trace_summary.update({'modules': sorted(modules), 'spanKinds': sorted(kinds),
                              'operations': sorted({span.get('name', '') for span in spans})})
        if not {'provider', 'reconciliation', 'cases', 'audit'}.issubset(modules) or not {'SERVER', 'PRODUCER', 'CONSUMER'}.issubset(kinds):
            return None
        assert all(span.get('serviceName') == 'reconflow' for span in spans)
        return {'spanCount': len(spans), **trace_summary}
    try:
        trace = wait(exported, 'indexed HTTP/Kafka/module trace', timeout=args.timeout)
    except DemoError as error:
        raise DemoError(str(error) + '; observed: ' + json.dumps(trace_summary)) from None

    audit = wait(lambda: next((row for row in api.get('/api/audit/CASE/' + case_id)
                              if row['action'] == 'CASE_OPENED'), None), 'committed case audit')
    def indexed_log():
        logs = search('reconflow-logs-*', {'term': {'traceId': trace_id}})
        for log in logs:
            if field(log, 'log.attributes.workflow@action') == 'CASE_OPENED' and field(log, 'log.attributes.workflow@resource_id') == case_id:
                assert field(log, 'resource.attributes.service@name') == 'reconflow'
                assert field(log, 'log.attributes.workflow@event_id') == audit['eventId']
                assert field(log, 'log.attributes.workflow@correlation_id') == audit['correlationId']
                assert log.get('spanId') and log.get('time') and field(log, 'severityText') == 'INFO'
                return {'eventId': audit['eventId'], 'correlationId': audit['correlationId'], 'spanId': log['spanId']}
        return None
    log_evidence = wait(indexed_log, 'indexed committed CASE_OPENED with business and trace correlation', timeout=args.timeout)
    expected = ['jvm.memory.used', 'http.server.requests', 'spring.kafka.template', 'spring.kafka.listener', 'module.events.published']
    missing_metrics = []
    metric_evidence = {}
    def metrics_present():
        metrics = search('ss4o_metrics-otel-reconflow-v2-*', {'range': {'time': {'gte': started}}})
        missing_metrics[:] = [name for name in expected if not any(m.get('name') == name for m in metrics)]
        if missing_metrics:
            return None
        for m in metrics:
            assert m.get('serviceName') == 'reconflow', m
            assert field(m.get('attributes', {}), 'resource.attributes.service@name') == 'reconflow', m
            attrs = m.get('attributes', {})
            def dimensions(value):
                for key, item in value.items():
                    leaf = key.split('.')[-1].lower().replace('_', '').replace('@', '').replace('-', '')
                    assert leaf not in {'tenantid', 'resourceid', 'caseid', 'transactionid', 'eventid',
                                        'correlationid', 'businessreference', 'reconciliationid', 'providertransactionid'}, attrs
                    if isinstance(item, dict):
                        dimensions(item)
                    else:
                        assert not any(identifier in str(item).lower() for identifier in
                                       (ref.lower(), case_id, rec_id, source['providerEventId'].lower())), attrs
            dimensions(attrs)
        for name in expected:
            rows = [m for m in metrics if m.get('name') == name]
            m = rows[0]
            assert m.get('kind') in ('GAUGE', 'SUM', 'HISTOGRAM') and m.get('time'), m
            if name == 'jvm.memory.used':
                assert m['kind'] == 'GAUGE' and m['unit'] == 'bytes' and any(row.get('value', 0) > 0 for row in rows), rows
            elif name in ('http.server.requests', 'spring.kafka.template', 'spring.kafka.listener'):
                assert m['kind'] == 'HISTOGRAM' and m['unit'] == 'seconds', m
                assert all(str(row.get('aggregationTemporality')).upper() in ('DELTA', 'AGGREGATION_TEMPORALITY_DELTA', '1') for row in rows), rows
                assert any(row.get('count', 0) > 0 and row.get('sum', 0) > 0 and row.get('bucketCountsList') for row in rows), rows
            else:
                assert m['kind'] == 'SUM' and m.get('unit') in ('', None), m
                assert all(row.get('aggregationTemporality') == 'AGGREGATION_TEMPORALITY_DELTA' for row in rows), rows
                assert any(row.get('value', 0) > 0 for row in rows), rows
            sample = next((row for row in rows if row.get('count', row.get('value', 0)) > 0), m)
            metric_evidence[name] = {key: sample.get(key) for key in
                                     ('kind', 'unit', 'aggregationTemporality', 'time', 'value', 'count', 'sum')}
            if sample.get('bucketCountsList'):
                metric_evidence[name]['histogramBucketCount'] = len(sample['bucketCountsList'])
        return True
    try:
        wait(metrics_present, 'fresh HTTP, Kafka, module and JVM indexed metrics', timeout=args.timeout)
    except DemoError as error:
        raise DemoError(str(error) + '; missing: ' + ', '.join(missing_metrics)) from None
    if args.log_file:
        def logged():
            for line in args.log_file.read_text().splitlines():
                try:
                    record = json.loads(line)
                except json.JSONDecodeError:
                    continue
                workflow = record.get('workflow', {})
                if (workflow.get('action') == 'CASE_OPENED' and workflow.get('resource_id') == case_id
                        and record.get('trace.id', record.get('traceId')) == trace_id):
                    assert workflow.get('event_id') and workflow.get('correlation_id')
                    return True
            return None
        wait(logged, 'committed structured audit log correlated to the exported trace')
    print(json.dumps({'traceId': trace_id, 'reconciliationId': rec_id, 'caseId': case_id,
                      'startedAt': started, 'metricsVerified': metric_evidence, 'indexedLogVerified': log_evidence,
                      'structuredConsoleVerified': bool(args.log_file), 'traceVerified': trace}, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (DemoError, AssertionError) as error:
        raise SystemExit('Observability verification failed: ' + str(error))
