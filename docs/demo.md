# Reproducible MVP demonstration

Start Compose and the application with the `demo` profile as described in the
repository README. Run `python3 dev/demo.py` from the repository root. It needs
only the Python standard library. Results are printed to stdout as JSON;
progress and safe errors use stderr. Tokens and passwords are never printed.

The default local sign-in is `admin` in the `reconflow` realm, with the seeded
demo password. The runner also signs in as `other-analyst` to verify denial in
both directions between the two tenants. Authentication uses the demo realm's
explicitly enabled password grant. A browser client should use the realm's
authorization-code/PKCE flow instead.

| Environment variable | Purpose |
| --- | --- |
| `DEMO_BASE_URL` | API base URL, default `http://localhost:8080` |
| `OIDC_ISSUER` | Demo realm URL, default `http://localhost:8180/realms/reconflow` |
| `DEMO_USER` / `DEMO_PASSWORD` | Alternate demo sign-in |
| `DEMO_BEARER_TOKEN` | Use an existing analyst/admin bearer token |
| `DEMO_OTHER_TOKEN` | Other tenant's token when supplying your own bearer token |
| `DEMO_ADMIN_TOKEN` | Administrator token for optional documentation imports |

Login sessions refresh tokens in memory when necessary. Supplied bearer tokens
must remain valid for the run. With an explicit bearer token and no other-tenant
token, the runner reports `tenantIsolationVerified: false`; it does not claim to
have tested another tenant. The normal default demo verifies both tenants.

The source references include a run ID. `--run-id my-review` replays an existing
run by finding and validating its original sources. Provider retries preserve
their original source identities, while each delivery receives a fresh transport
ID. Matching decisions, case IDs and original audit actions remain unchanged.
The runner does not delete old demo activity or force financial decisions.

Missing counterparts expire after the matching window. The `demo` profile uses
30 seconds to make these scenarios practical to review. The default is still
`PT10M`; `reconflow.reconciliation.matching-window` controls newly created windows.
Existing stored deadlines are preserved. To run against the normal ten-minute
policy, increase the runner's `--timeout`, for example `--timeout 700`.

## Optional AI investigation

Set `AI_MODEL`, `AI_EMBEDDING_MODEL`, `AI_API_KEY`, and `TYPESAFE_API_KEY`, then
start with both profiles:

```sh
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo,ai
python3 dev/demo.py --investigate
```

The administrator imports a demo review policy and simulated provider settlement
guide, then requests an investigation of the USD 3.20 fee case. The result is
included in the JSON output. The documents describe reported evidence and the
human review requirement; they do not invent a contractual fee tariff.
Model and judgment endpoints can be overridden using the existing AI settings.
This optional path makes live external model calls; the regular demo needs no
AI credentials. `--investigation-timeout` bounds how long the runner waits.

The case remains unresolved. Review the actual source evidence and limitations
before selecting a resolution. An authorized analyst/admin can then call:

```text
POST /api/cases/{caseId}/resolution
Authorization: Bearer <your access token>
Content-Type: application/json

{
  "type": "ACCEPTED_FEE_VARIANCE",
  "reason": "Human review confirmed the reported fee evidence is acceptable.",
  "evidenceReferences": ["investigation:<investigationId>"]
}
```

Use the actual case and investigation IDs printed by the runner. Inspect
`GET /api/audit/CASE/{caseId}` afterward. The case becomes RESOLVED; the original
reconciliation remains REVIEW_REQUIRED. The runner never applies a suggested
resolution automatically.

## CI verification

The normal Maven suite covers financial invariants, idempotency, roles, tenant
isolation, retries and advisory AI boundaries. CI additionally starts the real
Compose services, launches the packaged application in demo mode, authenticates
through Keycloak, verifies all eight scenarios and their audit entries, checks
both tenant directions, then replays the same run and checks resource identities.
