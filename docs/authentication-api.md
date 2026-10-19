# Authentication and API

The API verifies RS256 bearer tokens against the configured OIDC issuer's JWKS,
including issuer, expiry and the `reconflow` audience. The signed `tenant_id`
claim selects a membership for the token subject. Every request loads the active
membership from PostgreSQL; its role is authoritative. Token realm roles and
`X-Tenant-Id` headers grant no permissions. Missing or disabled memberships fail
authentication. Valid viewers can read; analysts and administrators can record
internal transactions, review cases, add notes and resolve them.

All financial and case queries receive the authenticated tenant. Cross-tenant
IDs return the same 404 as missing records. Case writes retain their application
service role checks and server-controlled actor and timestamps. A resolved case
returns 409 on subsequent mutation. Financial source records and reconciliation
determinations are not edited by these endpoints.

This API slice uses stateless bearer headers only: no Basic authentication,
session authentication or cookie credentials. CSRF is disabled for that chain.
Browser sign-in will use authorization code with PKCE when the analyst UI is
added. The local realm already restricts its browser callback to localhost and
requires S256 PKCE. Direct password grants exist only for local CLI examples and
the realm contract test; disable them outside the demo.

## Local setup

Run `docker compose up -d` for PostgreSQL and Keycloak. With Java 25, run
`./mvnw spring-boot:run -Dspring-boot.run.profiles=demo`. The demo profile seeds
two tenants and four memberships, and disables the Kafka listener for this
API-only setup. Provider integration tests continue to exercise real Kafka;
enable it and configure `spring.kafka.bootstrap-servers` when running ingestion.

Keycloak is at http://localhost:8180. Demo users are `admin`, `analyst`, `viewer`
and `other-analyst`, each with password `demo-password`. The last belongs to a
separate tenant. These are development credentials. The imported realm and
membership subjects must agree. Repeatable demo seeds use `ON CONFLICT DO
NOTHING`, preserving intentional role or enablement changes. Demo data is not
loaded under the default profile.

Obtain a demo token:

```sh
curl -s http://localhost:8180/realms/reconflow/protocol/openid-connect/token \
  -d grant_type=password -d client_id=reconflow \
  -d username=analyst -d password=demo-password
```

Pass the response's `access_token` as `Authorization: Bearer TOKEN` to
http://localhost:8080/api/session. OpenAPI JSON is at `/v3/api-docs` and Swagger
UI at `/swagger-ui.html`; both require authentication. `/api/session` returns
the effective tenant, subject and membership role. Configure `OIDC_ISSUER` and
`OIDC_JWK_SET_URI` for another Keycloak deployment. Membership provisioning is
application data managed by deployment SQL in this slice; it is not inferred
from an administrator realm role. No registration or identity-management UI is
introduced.

## Endpoints

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/session` | Effective actor |
| POST | `/api/transactions` | Record an internal payment or refund |
| GET | `/api/transactions/{id}` | Read an internal transaction |
| GET | `/api/provider-transactions/{id}` | Read a normalized provider record |
| GET | `/api/reconciliations/{id}` | Read determination and findings |
| GET | `/api/transactions/{id}/reconciliation` | Find an internal record's reconciliation |
| GET | `/api/provider-transactions/{id}/reconciliation` | Find a provider record's reconciliation |
| GET | `/api/reconciliations/{id}/case` | Read the linked exception case |
| GET | `/api/cases/{id}` | Read case history and resolution |
| POST | `/api/cases/{id}/review` | Start human review |
| POST | `/api/cases/{id}/notes` | Append an immutable note |
| POST | `/api/cases/{id}/resolution` | Record the final human resolution |

Request schemas are generated from validated records in OpenAPI. Errors use
Problem Details: invalid input 400, authentication 401, role denial 403, missing
or other-tenant records 404, and invalid state or concurrent update 409.

## Verification

The API integration suite uses signed RSA tokens and a local JWKS endpoint with
the production JWT decoder and PostgreSQL. It checks invalid signatures,
issuers, audiences, expired tokens, unknown and disabled memberships, ignored
token roles and tenant headers, tenant isolation, validation and case lifecycle.
A separate Testcontainers test starts the imported Keycloak realm and verifies
the four demo identities' signed tokens, tenant claims and audience.
