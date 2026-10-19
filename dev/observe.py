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
    parser.add_argument('--jaeger-url', default='http://localhost:16686')
    parser.add_argument('--log-file', type=Path)
    parser.add_argument('--expect-broker-down', action='store_true')
    args = parser.parse_args()
    management = args.management_url.rstrip('/')
    if args.expect_broker_down:
        wait(lambda: read(management + '/actuator/health/readiness')[0] == 503, 'broker-down readiness')
        status, live = read(management + '/actuator/health/liveness')
        assert status == 200 and live == {'status': 'UP'}, 'Dependency failure must not fail process liveness'
        print('Broker failure changes readiness to DOWN while liveness stays UP.')
        return
    for group in ('liveness', 'readiness'):
        wait(lambda: read(management + '/actuator/health/' + group) == (200, {'status': 'UP'}), group)
    for url in (args.base_url + '/actuator/prometheus', management + '/api/session', management + '/actuator/env'):
        assert read(url)[0] in (401, 403, 404), 'Operator and business listeners must remain separated'
    assert read(args.base_url + '/api/session')[0] == 401
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
    trace_summary = {}
    def exported():
        status, payload = read(args.jaeger_url.rstrip('/') + '/api/traces/' + trace_id)
        if status != 200 or not payload.get('data'):
            return None
        trace = payload['data'][0]
        spans = trace['spans']
        tags = [{tag['key']: tag['value'] for tag in span.get('tags', [])} for span in spans]
        modules = {tag.get('module.identifier') for tag in tags}
        kinds = {tag.get('span.kind') for tag in tags}
        trace_summary.update({'modules': sorted(modules - {None}), 'spanKinds': sorted(kinds - {None}),
                              'operations': sorted({span['operationName'] for span in spans})})
        if not {'provider', 'reconciliation', 'cases', 'audit'}.issubset(modules) or not {'producer', 'consumer'}.issubset(kinds):
            return None
        assert any(process['serviceName'] == 'reconflow' for process in trace['processes'].values())
        return {'spanCount': len(spans), 'modules': sorted(modules - {None}), 'spanKinds': sorted(kinds - {None})}
    try:
        trace = wait(exported, 'exported HTTP/Kafka/module trace', timeout=90)
    except DemoError as error:
        raise DemoError(str(error) + '; observed: ' + json.dumps(trace_summary)) from None
    missing_metrics = []
    def metrics_present():
        status, metrics = read(management + '/actuator/prometheus', text=True)
        if status != 200:
            return None
        expected = ['http_server_requests_seconds_count', 'spring_kafka_template_seconds_count',
                    'spring_kafka_listener_seconds_count', 'module_events_published_total', 'jvm_memory_used_bytes']
        missing_metrics[:] = [name for name in expected if name not in metrics]
        if missing_metrics:
            return None
        assert ref not in metrics and 'tenant_id=' not in metrics, 'Business IDs must not become metric labels'
        return True
    try:
        wait(metrics_present, 'HTTP, Kafka, module and JVM metrics')
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
                      'metricsVerified': True, 'structuredLogVerified': bool(args.log_file), **trace}, indent=2))


if __name__ == '__main__':
    try:
        main()
    except (DemoError, AssertionError) as error:
        raise SystemExit('Observability verification failed: ' + str(error))
