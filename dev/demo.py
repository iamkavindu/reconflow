#!/usr/bin/env python3
"""Generate and verify the agreed ReconFlow MVP scenarios through authenticated APIs."""

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import datetime, timezone
from decimal import Decimal


class DemoError(Exception):
    pass


class ApiError(DemoError):
    def __init__(self, status, path):
        self.status = status
        super().__init__(f"HTTP {status or 'connection failure'} at {path}")


class Login:
    def __init__(self, issuer, username, password):
        self.url = issuer.rstrip("/") + "/protocol/openid-connect/token"
        self.refresh_token = None
        self.token = self.exchange({"grant_type": "password", "username": username, "password": password}, wait=True)

    def exchange(self, fields, wait=False):
        deadline = time.monotonic() + (60 if wait else 15)
        while True:
            request = urllib.request.Request(self.url, data=urllib.parse.urlencode({"client_id": "reconflow", **fields}).encode(),
                                             headers={"Content-Type": "application/x-www-form-urlencoded"})
            try:
                with urllib.request.urlopen(request, timeout=10) as response:
                    data = json.load(response)
                self.refresh_token = data.get("refresh_token", self.refresh_token)
                return data["access_token"]
            except urllib.error.HTTPError as error:
                if not wait or error.code not in (404, 502, 503) or time.monotonic() >= deadline:
                    raise DemoError(f"Demo sign-in failed (HTTP {error.code}); check the realm, user and credentials.") from None
            except (urllib.error.URLError, TimeoutError):
                if not wait or time.monotonic() >= deadline:
                    raise DemoError("The demo identity provider did not become available.") from None
            time.sleep(1)

    def refresh(self):
        if not self.refresh_token:
            raise DemoError("The session expired; obtain a new bearer token.")
        return self.exchange({"grant_type": "refresh_token", "refresh_token": self.refresh_token})


class Api:
    def __init__(self, base_url, token, refresh=None):
        self.base = base_url.rstrip("/")
        self.token = token
        self.refresh = refresh

    def request(self, path, method="GET", body=None, missing=False, retry_delivery=False):
        # Small fixed demo amounts are encoded as JSON numbers; verification uses Decimal.
        encoded = None if body is None else json.dumps(body, default=lambda value: float(value) if isinstance(value, Decimal) else str(value)).encode()
        for attempt in range(4):
            request = urllib.request.Request(self.base + path, data=encoded, method=method,
                                             headers={"Authorization": "Bearer " + self.token, "Content-Type": "application/json"})
            try:
                with urllib.request.urlopen(request, timeout=15) as response:
                    return json.loads(response.read(), parse_float=Decimal)
            except urllib.error.HTTPError as error:
                if error.code == 404 and missing:
                    return None
                if error.code == 401 and self.refresh and attempt == 0:
                    self.token = self.refresh()
                    continue
                if not (retry_delivery and error.code == 503 and attempt < 2):
                    raise ApiError(error.code, path) from None
            except (urllib.error.URLError, TimeoutError):
                if not retry_delivery or attempt >= 2:
                    raise ApiError(0, path) from None
            time.sleep(0.5)
        raise ApiError(503, path)

    def get(self, path, missing=False):
        return self.request(path, missing=missing)

    def post(self, path, body=None, retry_delivery=False):
        return self.request(path, "POST", body, retry_delivery=retry_delivery)

    def find(self, path, **filters):
        return self.get(path + "?" + urllib.parse.urlencode({**filters, "limit": 2}))


def now():
    return datetime.now(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def identifier(record, field):
    return record[field]["value"]


def wait_for(read, timeout, description):
    deadline = time.monotonic() + timeout
    while True:
        result = read()
        if result is not None:
            return result
        if time.monotonic() >= deadline:
            raise DemoError(f"Timed out waiting for {description}; check the matching window, Kafka and application logs.")
        time.sleep(0.3)


def wait_for_api(api):
    deadline = time.monotonic() + 60
    while True:
        try:
            return api.get("/api/session")
        except ApiError as error:
            if error.status not in (0, 502, 503) or time.monotonic() >= deadline:
                raise
            time.sleep(1)


def transaction(api, reference, kind="PAYMENT", amount="100.00", currency="USD"):
    rows = api.find("/api/transactions", reference=reference)
    if len(rows) > 1:
        raise DemoError(f"Ambiguous existing internal records for {reference}; use a new run ID.")
    if rows:
        record = rows[0]
        if record["type"] != kind or record["currency"] != currency or Decimal(str(record["amount"])) != Decimal(amount):
            raise DemoError(f"Existing source differs for {reference}; use a new run ID.")
        return record
    return api.post("/api/transactions", {"businessReference": reference, "type": kind, "amount": Decimal(amount),
                                         "currency": currency, "occurredAt": now()})


def provider(api, reference, run_id, name, occurred_at, amount="100.00", currency="USD", kind="PAYMENT", deliveries=1):
    source = {"providerEventId": f"evt-{run_id}-{name}", "providerTransactionReference": f"PAY-{run_id}-{name}",
              "businessReference": reference, "type": kind, "amount": Decimal(amount), "currency": currency,
              "status": "SUCCEEDED", "occurredAt": occurred_at}
    for _ in range(deliveries):
        api.post("/api/provider-events/simulate", source, retry_delivery=True)
    return source["providerTransactionReference"]


def verify(api, item, timeout):
    def final_result():
        rows = api.find("/api/reconciliations", reference=item["reference"])
        if len(rows) > 1:
            raise DemoError(f"More than one effective reconciliation for {item['reference']}.")
        if not rows or rows[0]["status"] == "PENDING":
            return None
        return rows[0]

    result = wait_for(final_result, timeout, item["name"])
    types = {finding["type"] for finding in result["discrepancies"]}
    if result["status"] != item["expected"] or types != set(item["discrepancies"]):
        raise DemoError(f"Unexpected result for {item['name']}: {result['status']} / {sorted(types)}. Use a new run ID if its earlier matching window expired.")
    rec_id = identifier(result, "reconciliationId")
    def audit_recorded(resource_type, resource_id, action):
        rows = api.get(f"/api/audit/{resource_type}/{resource_id}")
        matches = [row for row in rows if row["action"] == action]
        if len(matches) > 1:
            raise DemoError(f"Duplicate effective audit actions for {resource_id}.")
        return matches or None

    wait_for(lambda: audit_recorded("RECONCILIATION", rec_id, "RECONCILIATION_" + result["status"]), timeout, "reconciliation audit")
    case_path = f"/api/reconciliations/{rec_id}/case"
    if result["status"] == "MATCHED":
        if api.get(case_path, missing=True) is not None:
            raise DemoError("A successful match unexpectedly created a case.")
        case_id = None
    else:
        case_id = identifier(wait_for(lambda: api.get(case_path, missing=True), timeout, "case creation"), "caseId")
        wait_for(lambda: audit_recorded("CASE", case_id, "CASE_OPENED"), timeout, "case audit")
    source_id = None
    if item.get("providerReference"):
        rows = wait_for(lambda: api.find("/api/provider-transactions", providerReference=item["providerReference"]) or None,
                        timeout, "provider ingestion")
        if len(rows) != 1:
            raise DemoError("Provider delivery created more than one logical source record.")
        source_id = identifier(rows[0], "providerTransactionId")
        wait_for(lambda: audit_recorded("PROVIDER_TRANSACTION", source_id, "PROVIDER_TRANSACTION_RECORDED"), timeout, "provider audit")
    return {"name": item["name"], "reference": item["reference"], "status": result["status"],
            "discrepancies": sorted(types), "reconciliationId": rec_id, "caseId": case_id,
            "providerTransactionId": source_id, "auditVerified": True}


def scenarios(api, run_id):
    items = []
    definitions = [
        ("perfect_match", "1001", "MATCHED", [], "100.00", "USD", "PAYMENT"),
        ("provider_fee", "1002", "REVIEW_REQUIRED", ["AMOUNT_MISMATCH", "FEE_VARIANCE"], "96.80", "USD", "PAYMENT"),
        ("amount_mismatch", "1003", "REVIEW_REQUIRED", ["AMOUNT_MISMATCH"], "98.00", "USD", "PAYMENT"),
        ("currency_mismatch", "1004", "REVIEW_REQUIRED", ["CURRENCY_MISMATCH"], "100.00", "EUR", "PAYMENT"),
        ("duplicate_webhook", "1005", "MATCHED", [], "100.00", "USD", "PAYMENT"),
        ("missing_provider", "1006", "UNMATCHED", ["MISSING_PROVIDER_TRANSACTION"], None, "USD", "PAYMENT"),
        ("orphan_provider", "1007", "UNMATCHED", ["MISSING_INTERNAL_TRANSACTION"], "100.00", "USD", "PAYMENT"),
        ("refund_mismatch", "1008", "REVIEW_REQUIRED", ["REFUND_MISMATCH"], "20.00", "USD", "REFUND"),
    ]
    for name, order, expected, discrepancies, amount, currency, kind in definitions:
        reference = f"ORD-{order}-{run_id}"
        internal = None if name == "orphan_provider" else transaction(api, reference, kind, "25.00" if kind == "REFUND" else "100.00")
        existing = api.find("/api/provider-transactions", providerReference=f"PAY-{run_id}-{name}")
        occurred_at = internal["occurredAt"] if internal else (existing[0]["occurredAt"] if existing else now())
        if name == "provider_fee":
            api.post("/api/settlements", {"provider": "stripe", "providerSettlementReference": f"SET-{run_id}",
                     "currency": "USD", "grossAmount": 100, "feeAmount": Decimal("3.20"), "netAmount": Decimal("96.80"),
                     "settledAt": occurred_at, "lines": [{"providerTransactionReference": f"PAY-{run_id}-{name}",
                         "grossAmount": 100, "feeAmount": Decimal("3.20"), "netAmount": Decimal("96.80")}]})
        external = None if amount is None else provider(api, reference, run_id, name, occurred_at, amount, currency, kind,
                                                       3 if name == "duplicate_webhook" else 1)
        items.append({"name": name, "reference": reference, "expected": expected, "discrepancies": discrepancies, "providerReference": external})
    return items


def verify_isolation(api, other, run_id, first):
    other_record = transaction(other, "ORD-OTHER-" + run_id)
    own = api.find("/api/transactions", reference=first["reference"])[0]
    for client, record in ((api, other_record), (other, own)):
        try:
            client.get("/api/transactions/" + identifier(record, "transactionId"))
        except ApiError as error:
            if error.status == 404:
                continue
            raise
        raise DemoError("Tenant isolation verification failed.")
    return True


def investigate(api, admin, fee_case, timeout):
    for kind, title, content in (
        ("POLICY", "Demo fee review policy", "A reported processing fee can explain a gross/net difference. An analyst must verify settlement evidence and document a human decision before accepting the variance. A reported fee does not prove the contractual tariff."),
        ("PROVIDER_DOCUMENTATION", "Simulated provider settlement guide", "The simulated provider reports gross, fee and net settlement amounts. Gross minus the reported fee equals net. These reports provide evidence; they do not independently validate the fee contract."),
    ):
        admin.post("/api/knowledge/documents", {"kind": kind, "title": title, "content": content})
    requested = api.post(f"/api/cases/{fee_case['caseId']}/investigations")
    inv_id = identifier(requested, "investigationId")
    print(f"Investigation requested: {inv_id}; case {fee_case['caseId']} remains under human control.", file=sys.stderr)

    def complete():
        value = api.get("/api/investigations/" + inv_id)
        return value if value["status"] in ("COMPLETED", "FAILED") else None

    result = wait_for(complete, timeout, "AI investigation")
    if result["status"] != "COMPLETED":
        raise DemoError("The investigation retained a FAILED attempt; inspect its safe failure reason through the API.")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.getenv("DEMO_BASE_URL", "http://localhost:8080"))
    parser.add_argument("--issuer", default=os.getenv("OIDC_ISSUER", "http://localhost:8180/realms/reconflow"))
    parser.add_argument("--run-id", default=uuid.uuid4().hex[:8])
    parser.add_argument("--timeout", type=int, default=120)
    parser.add_argument("--investigate", action="store_true")
    parser.add_argument("--investigation-timeout", type=int, default=300)
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,24}", args.run_id) or args.timeout < 1 or args.investigation_timeout < 1:
        parser.error("Use a 1–24 character run ID and positive timeouts.")
    print("Demo run: " + args.run_id, file=sys.stderr)
    token = os.getenv("DEMO_BEARER_TOKEN")
    password = os.getenv("DEMO_PASSWORD", "demo-password")
    login = None if token else Login(args.issuer, os.getenv("DEMO_USER", "admin"), password)
    api = Api(args.base_url, token or login.token, None if login is None else login.refresh)
    session = wait_for_api(api)
    if session["role"] not in ("ADMIN", "ANALYST"):
        raise DemoError("Generating demo activity requires an analyst or administrator.")
    admin_token = os.getenv("DEMO_ADMIN_TOKEN")
    admin = Api(args.base_url, admin_token) if admin_token else api
    if args.investigate and admin.get("/api/session")["role"] != "ADMIN":
        raise DemoError("Policy imports require an administrator; set DEMO_ADMIN_TOKEN.")
    items = scenarios(api, args.run_id)
    print("Waiting for the eight deterministic reconciliation outcomes…", file=sys.stderr)
    results = [verify(api, item, args.timeout) for item in items]
    other_token = os.getenv("DEMO_OTHER_TOKEN")
    other_login = Login(args.issuer, "other-analyst", password) if not token and not other_token else None
    other = Api(args.base_url, other_token or other_login.token, None if other_login is None else other_login.refresh) if other_token or other_login else None
    isolation = verify_isolation(api, other, args.run_id, results[0]) if other else False
    output = {"runId": args.run_id, "tenantId": identifier(session, "tenantId"), "scenarios": results,
              "tenantIsolationVerified": isolation, "dashboard": api.get("/api/dashboard")}
    if args.investigate:
        output["investigation"] = investigate(api, admin, next(value for value in results if value["name"] == "provider_fee"), args.investigation_timeout)
    print(json.dumps(output, indent=2, default=str))


if __name__ == "__main__":
    try:
        main()
    except DemoError as error:
        print("Demo failed: " + str(error), file=sys.stderr)
        sys.exit(1)
