# Analyst and administrator workspace

Start Compose and the application with the `demo` profile, then open
`http://localhost:8080`. Use **Sign in** and the local Keycloak realm's demo
accounts: `admin`, `analyst`, or `viewer`, with password `demo-password`.
The other tenant's analyst is `other-analyst`.

The workspace includes:

- Six operational dashboard counts, refreshed on demand.
- Search and pagination for internal transactions, provider records,
  reconciliations and cases, using the existing ID/reference/status/date filters.
- Immutable source evidence, rule evaluations, discrepancies, settlement lines,
  and per-resource audit history with earlier-entry pagination.
- Analyst/admin transaction entry and provider event simulation. Preserve source
  event IDs when retrying uncertain provider delivery; acceptance is asynchronous.
- Case review and notes, investigation requests/history, structured AI findings,
  limitations, screening results, and tenant-scoped cited documentation passages.
- Explicit human resolution with a required reason and final-decision checkbox.
  AI suggestions never fill or submit the resolution form automatically.
- Administrator-only policy/provider/past-case document import. Indexing requires
  the AI configuration; a disabled integration reports its safe API error.

Generate the eight scenarios using `python3 dev/demo.py`, then refresh the UI.
The dashboard and lists do not poll automatically. Refresh a case to observe an
in-progress investigation or newly appended audit events. AI requests require
the `ai` profile and valid external service configuration; the basic workspace
and deterministic reconciliation do not.

## Browser access and deployment

The static shell and `/ui/config` are public. Business APIs, API documentation,
financial evidence and case actions still require a verified bearer token.
The public config contains only the configured issuer and client ID.

The browser uses the configured Keycloak issuer's authorization-code flow with
S256 PKCE, a one-use random state, a five-minute handshake lifetime and an exact
authorization-response issuer check. It requests no offline access and uses
`/api/session` as the authoritative source of identity, organization and role.
It does not trust browser-decoded claims for access decisions.

Access tokens exist only in JavaScript memory. Session storage holds only the
pending code verifier/state during redirect and is cleared before code exchange.
Refreshing the page or signing out clears the workspace session. Sign in again
to start a new session; the identity provider's separate SSO session is not
revoked by local sign-out. To change accounts on a re-authentication screen,
use Keycloak’s restart-login control next to the remembered username. Tokens and passwords are never placed in UI URLs or
persistent browser storage. All business text is rendered as text, never HTML.

Use HTTPS for hosted application and issuer URLs. Localhost HTTP is supported
for development. Register the application's exact origin/root redirect with the
Keycloak public client and web origins; the demo realm includes ports 8080 and
18080 (the CI browser check). Set the issuer/JWK settings as documented in the
API authentication guide. The content security policy allows API requests to
this application and token exchange with the configured issuer only.

Browser checks require a current browser with Web Crypto and JSON.parse source
context support, preserving exact monetary JSON values instead of converting
amounts through binary floating-point arithmetic. The UI does no financial
calculations. API validation remains authoritative for every submitted field.

## Verification

CI starts the packaged application and real Compose services, generates/replays
the eight scenarios, then runs Chromium against Keycloak and the application.
The browser check signs in, searches and opens fee evidence, records review and
a note, confirms AI-disabled behavior without resolving the case, applies an
explicit human resolution, verifies the audit entry and preserved reconciliation,
and checks viewer controls. A second check rejects unsolicited login callbacks
before any token exchange. Backend tests continue to enforce roles and tenant
access independently of UI controls.

An additional AI-enabled packaged-application check uses local protocol fixtures
and real pgvector/Keycloak/Kafka. It requests an investigation through the browser,
reads its cited passage, verifies that AI does not fill or submit the human
resolution form, then explicitly resolves the case and checks audit and the
retained reconciliation result. Run it with `npm --prefix dev/browser run test:ai`
against the dedicated CI acceptance setup; it reads the AI demo's result file.
These checks use no live model calls and do not establish AI quality. See the
[acceptance guide](acceptance.md) for live verification.
