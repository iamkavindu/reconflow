#!/usr/bin/env python3
"""Idempotent local schemas and saved objects. No deletion of telemetry."""
import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent


def request(url, method='GET', body=None, content_type='application/json'):
    headers = {'Content-Type': content_type, 'osd-xsrf': 'reconflow-bootstrap'}
    if body is not None and not isinstance(body, bytes):
        body = json.dumps(body).encode()
    try:
        with urllib.request.urlopen(urllib.request.Request(url, data=body, headers=headers, method=method), timeout=10) as r:
            return json.load(r)
    except urllib.error.HTTPError as e:
        raise RuntimeError(f'{method} {url}: HTTP {e.code}: {e.read().decode()[:1000]}') from None


def wait(url, timeout, check=lambda value: True):
    deadline = time.monotonic() + timeout
    last = ''
    while time.monotonic() < deadline:
        try:
            result = request(url)
            if check(result):
                return result
            last = str(result)[:500]
        except (OSError, RuntimeError) as e:
            last = str(e)
        time.sleep(1)
    raise RuntimeError(f'Timed out after {timeout}s waiting for {url}: {last}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--opensearch-url', default='http://127.0.0.1:9200')
    parser.add_argument('--dashboards-url', default='http://127.0.0.1:5601')
    parser.add_argument('--schemas-only', action='store_true')
    parser.add_argument('--timeout', type=int, default=180)
    args = parser.parse_args()
    os = args.opensearch_url.rstrip('/')
    wait(os + '/_cluster/health', args.timeout, lambda v: v['status'] in ('yellow', 'green'))
    for path in sorted((ROOT / 'policies').glob('*.json')):
        policy_url = os + '/_plugins/_ism/policies/reconflow-' + path.stem
        suffix = ''
        try:
            old = request(policy_url)
            suffix = f"?if_seq_no={old['_seq_no']}&if_primary_term={old['_primary_term']}"
        except RuntimeError as error:
            if 'HTTP 404' not in str(error):
                raise
        request(policy_url + suffix, 'PUT', json.loads(path.read_text()))
    for path in sorted((ROOT / 'templates').glob('*.json')):
        request(os + '/_index_template/reconflow-' + path.stem, 'PUT', json.loads(path.read_text()))
    if not args.schemas_only:
        dashboards = args.dashboards_url.rstrip('/')
        wait(dashboards + '/api/status', args.timeout,
             lambda value: value.get('status', {}).get('overall', {}).get('state') == 'green')
        boundary = 'reconflow-saved-objects'
        payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="saved-objects.ndjson"\r\nContent-Type: application/ndjson\r\n\r\n'.encode()
                   + (ROOT / 'saved-objects.ndjson').read_bytes() + f'\r\n--{boundary}--\r\n'.encode())
        result = request(dashboards + '/api/saved_objects/_import?overwrite=true', 'POST', payload, 'multipart/form-data; boundary=' + boundary)
        if not result.get('success'):
            raise RuntimeError('Saved object import failed: ' + json.dumps(result))
    print(json.dumps({'schemas': 'applied', 'savedObjects': 'skipped' if args.schemas_only else 'imported'}))


if __name__ == '__main__':
    try:
        main()
    except (OSError, RuntimeError, ValueError) as error:
        raise SystemExit('Bootstrap failed: ' + str(error))
