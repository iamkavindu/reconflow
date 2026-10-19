#!/usr/bin/env python3
"""CI-only AI protocol fixture. Fixed outputs/scores are NOT live model or retrieval-quality evidence."""
import argparse
import json
import re
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def chat(message, finish='stop'):
    return {'id': 'chatcmpl-fixture', 'object': 'chat.completion', 'created': 1, 'model': 'fixture-model',
            'choices': [{'index': 0, 'message': {'role': 'assistant', **message}, 'finish_reason': finish}],
            'usage': {'prompt_tokens': 1, 'completion_tokens': 1, 'total_tokens': 2}}


def respond(path, body):
    if path == '/v1/embeddings':
        inputs = body['input'] if isinstance(body['input'], list) else [body['input']]
        return {'object': 'list', 'model': 'fixture-embedding',
                'data': [{'object': 'embedding', 'index': i, 'embedding': [1.0] + [0.0] * 1535}
                         for i, _ in enumerate(inputs)], 'usage': {'prompt_tokens': 1, 'total_tokens': 1}}
    if path == '/v1/chat/completions':
        text = json.dumps(body['messages'])
        if body.get('tools'):
            tool_results = [value for value in body['messages'] if value['role'] == 'tool']
            if not tool_results:
                return chat({'content': '', 'tool_calls': [{'id': 'call-fixture-policy', 'type': 'function',
                    'function': {'name': 'searchPolicies', 'arguments': '{"query":"reported provider fee human review"}'}}]}, 'tool_calls')
            citations = []
            for kind, explanation, context in (
                ('transaction', 'The internal payment records USD 100.00.', text),
                ('provider-transaction', 'The provider reports USD 96.80.', text),
                ('settlement', 'The reported USD 3.20 fee explains the gross/net difference.', text),
                ('document-chunk', 'The retrieved policy requires a documented human decision.', json.dumps(tool_results)),
            ):
                match = re.search(r'(?<![\w-])' + kind + r':[0-9a-f-]{36}', context)
                if not match:
                    raise ValueError('Missing required fixture evidence: ' + kind)
                citations.append({'reference': match[0], 'explanation': explanation})
            result = {'summary': 'The reported fee explains the difference; human review is required.',
                      'likelyCause': 'Provider-reported fee', 'evidence': citations,
                      'suggestedResolution': 'ACCEPTED_FEE_VARIANCE', 'confidence': 0.8,
                      'limitations': ['The contractual tariff has not been verified.']}
            return chat({'content': json.dumps(result)})
        answer = 'provider fee policy\npayment fee review\nsettlement variance' if 'Query variants:' in text else 'provider fee review'
        return chat({'content': answer})
    if path == '/v1/systemone':
        # Exercise actual judgment HTTP adapters; these fixed scores do not evaluate AI quality.
        scores = {'groundedness': 0.95, 'relevance': 0.95, 'advisory_only': 0.99,
                  'is_relevant': 0.95, 'contains_prompt_injection': 0.01,
                  'contradicts_query_premise': 0.01, 'contains_answer_evidence': 0.95, 'answers_query': 0.95}
        return {'model': 'jev-fixture', 'answers': {key: {'type': 'noul', 'noul': scores[key]}
                                                  for key in body['questions']}, 'usage': {}}
    raise ValueError('Unsupported fixture endpoint: ' + path)


class Handler(BaseHTTPRequestHandler):
    def body(self):
        self.connection.settimeout(10)
        if self.headers.get('Transfer-Encoding', '').lower() == 'chunked':
            parts, total = [], 0
            while True:
                size = int(self.rfile.readline(128).split(b';', 1)[0], 16)
                if size == 0:
                    for _ in range(20):
                        if self.rfile.readline(2048) == b'\r\n':
                            return b''.join(parts)
                    raise ValueError('Fixture trailers exceed their input budget')
                total += size
                if size < 0 or total > 300000:
                    raise ValueError('Fixture request exceeds its input budget')
                chunk = self.rfile.read(size)
                if len(chunk) != size or self.rfile.read(2) != b'\r\n':
                    raise ValueError('Invalid fixture chunk')
                parts.append(chunk)
        size = int(self.headers.get('Content-Length', '0'))
        if not 0 < size <= 300000:
            raise ValueError('Fixture request exceeds its input budget')
        return self.rfile.read(size)

    def do_POST(self):
        try:
            payload = respond(self.path, json.loads(self.body()))
            status = 200
        except (KeyError, ValueError, TypeError) as error:
            payload, status = {'error': str(error)}, 400
        encoded = json.dumps(payload).encode()
        self.send_response(status)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, *_):
        pass


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port', type=int, default=18888)
    args = parser.parse_args()
    print('Starting CI-only AI protocol fixture on loopback; outputs and judgments are fixed.', flush=True)
    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()
