# Offline-first readiness and integration boundary

This document describes the checked-out Android prototype and a future design.
It does not claim release readiness, a running sync service, or interoperability
with the separate `-NIKNOLIG-Defense` repository.

## Current inventory

Status labels describe this checkout only: `VORHANDEN` means code or configuration
exists, `FUNKTIONSFÄHIG` means the named test demonstrated the limited behavior,
`TEILWEISE` means only a subset is implemented, `FEHLT` means no implementation
was found, `BLOCKIERT` means required external access/infrastructure is unavailable,
and `NICHT GETESTET` means runtime behavior has not been demonstrated.

| Area | Status | Evidence / boundary |
| --- | --- | --- |
| Android structure | VORHANDEN | Native `MainActivity` and `ElektroEngelActivity`; XML layouts and localized Android resources. |
| Offline assistance | VORHANDEN | Scenario text and keyword-matched answers are bundled with the app. Their Android runtime behavior on this task branch was not exercised because Gradle could not resolve AGP. |
| Local preferences | TEILWEISE | Optional general-interest categories are stored in app-private `SharedPreferences` after consent and can be cleared. They are not a sync queue or encrypted database. |
| Internet status | NICHT GETESTET | Implementation exists on the current task branch and distinguishes offline, local-only, unverified, validated internet, and unknown. It is not confirmed merged or built on `main`; validated internet does not prove API reachability. |
| Server status | FUNKTIONSFÄHIG | Existing Node tests demonstrated the fixed HTTPS health endpoint behavior (5/5 tests in the previous validation); this does not demonstrate production deployment or application-data exchange. |
| BLE | TEILWEISE | Foreground, user-started discovery displays advertised name and signal strength for up to 30 seconds. Results are transient and cleared on leaving the Activity. |
| Device connection / GATT | FEHLT | No connection manager, device authentication, GATT read/write, trust decision, or device control was found in this checkout. |
| Durable offline outbox / sync | FEHLT | No queued mutations, sync worker, conflict model, provenance tracking, or retry state exists. |
| Data-age / last-sync display | FEHLT | There is no synchronized data cache or last-successful-sync timestamp to display. |
| Website offline behavior | FEHLT | The site is static but has no offline cache/service worker in this checkout. |
| API for knowledge exchange | BLOCKIERT | `server/health-server.mjs` exposes only `/v1/health`; there is no data backend or verified Defense API. |
| Local WLAN service | FEHLT | No local-network discovery or local service transport is implemented. |
| Cellular / alternate internet | TEILWEISE | Android reports default-network capabilities; the app does not select or manage cellular transport independently. |
| Satellite / mesh | FEHLT | No satellite hardware integration or general-purpose mesh transport is implemented or tested. |
| NIKNOLIG Defense / NOLD Edge repositories | BLOCKIERT | GitHub account repository search exposed only `NIKNOLIG`; direct reads of the named Defense and NOLD Edge repositories returned 404. Their code, branches, APIs, and workflows could not be inspected. No changes were made outside NIKNOLIG. |
| CI | TEILWEISE | Android build/test and website deploy workflows exist. The last observed `main` Android and website runs succeeded before the task-branch connectivity change. The task branch's Android run is `action_required` with no jobs; release signing/publication require configured approvals/secrets and separate review. |

The Android build was attempted once locally for the connectivity change and stopped
before project configuration because Gradle could not resolve Android Gradle Plugin
8.11.1 from the configured repositories. GitHub Actions for the change requires
approval and has no jobs yet; it is not evidence of a successful build. The successful
`main` Android run predates this branch change.

## Connectivity status semantics

The live UI status is based on Android `ConnectivityManager` network capability
signals while each Activity is visible. It is a reachability hint, not a server
health check and not a guarantee that every request will succeed. The BLE search
and bundled assistant remain local functions when the internet is unavailable.
The monitor unregisters with the Activity lifecycle and does not start background
polling or transmit application data.

## Communication-path status

| Path | Status | What is actually available |
| --- | --- | --- |
| Internet / HTTPS | TEILWEISE | Optional, explicitly started health request to a configured HTTPS endpoint. No authenticated data API exists. |
| Android default network | NICHT GETESTET | OS-reported validated/unvalidated connectivity is displayed on the current branch; this does not establish which physical transport is active or prove server availability. |
| BLE | TEILWEISE | Foreground discovery only. No pairing, connection, GATT operations, or device command path. |
| Local Wi-Fi | FEHLT | No LAN service discovery, peer authentication, or local API. |
| Cellular | TEILWEISE | May carry Android's default internet connection; no cellular-specific selection, fallback, or delivery guarantee. |
| Offline message queue | FEHLT | No durable outbox, retry scheduler, or restart recovery. |
| Satellite / mesh | FEHLT | No hardware integration, protocol, or tested relay capability. |
| Website offline cache | FEHLT | Static hosting does not itself make the website available without a network connection. |

## Staged implementation plan

1. **Inventory and safety boundaries — complete for this checkout.** Preserve the
   existing Activities, local assistant, BLE lifecycle, and build workflows. Do
   not infer support from the separate Defense project.
2. **Offline status and local baseline — partially complete.** Show lifecycle-bound
   connectivity state on both app screens; keep bundled scenarios and local answers
   available. Test state classification. Verify UI states on physical Android
   devices before release.
3. **Persistence and recovery — deferred.** Add durable queued data only when a
   concrete user-approved offline mutation exists. First define expiry, deletion,
   process-death recovery, encryption/key recovery, and backup behavior; do not
   treat current preferences as a sync queue.
4. **BLE lifecycle — discovery only in this checkout.** Add connection/GATT only
   for a specified device and use case, with explicit permission, authenticated
   identity, user approval, bounded operations, and hardware-backed tests.
5. **Sync/API — contract draft only below.** Do not send data until both projects
   agree on the contract, identity model, server deployment, consent UX, and
   operational ownership.
6. **Other communication — deferred.** Treat cellular, internet, local Wi-Fi,
   Bluetooth, and any future satellite hardware as independent transports with
   separate availability and security checks. There is no universal mesh or
   Starlink integration.

## Proposed knowledge-exchange API contract (not implemented)

NIKNOLIG and Defense remain separate applications and repositories. Any future
exchange uses a narrowly scoped HTTPS service; neither app gets direct access to
the other's local database or trust zone.

### Transport, identity, and permissions

- Version every route under `/v1`; require HTTPS with normal certificate
  verification. Do not permit cleartext fallback.
- Require a short-lived, audience-bound bearer token from a separately operated
  identity provider. The client must not contain a client secret or API key.
  Validate issuer, audience, expiry, signature, and scopes server-side.
- Use only `knowledge:read-approved`, `knowledge:submit-proposal`,
  `knowledge:edit-own-pending`, and `knowledge:revoke-own` scopes. Bind origin to
  the authenticated client identity; never trust a caller-provided origin alone.
- Require explicit user confirmation for submitting, editing, or revoking a
  proposal. Approval of a proposal is a separate human reviewer permission.
- Token revocation requires online introspection or a server-side denylist;
  otherwise short token expiry bounds (but does not eliminate) the revocation
  delay. Revoking one message marks it unavailable to future reads; already
  downloaded copies cannot be remotely erased and clients must expose local
  deletion behavior.

### Version 1 routes

| Route | Access | Result / failure behavior |
| --- | --- | --- |
| `GET /v1/health` | No application-data scope | Existing fixed service health response; no claim that a specific record operation will succeed. |
| `GET /v1/knowledge/messages/{id}` | `knowledge:read-approved` | Returns approved messages only. Return `404` for unknown, pending, rejected, or revoked records to avoid leaking their state. |
| `POST /v1/knowledge/messages` | `knowledge:submit-proposal` | Accepts a new proposal only after explicit confirmation; requires `Idempotency-Key`; returns `202` and a server receipt with `pending_review`. |
| `PUT /v1/knowledge/messages/{id}` | `knowledge:edit-own-pending` | Edits only the authenticated origin's pending proposal; requires `If-Match` with the current revision ETag. A stale revision returns `409` and preserves both versions. |
| `POST /v1/knowledge/messages/{id}/revoke` | `knowledge:revoke-own` | Records a revocation event for the authenticated origin's message; idempotent by revocation request ID. |

Unauthenticated requests receive `401`; insufficient scopes receive `403`;
invalid schemas receive `400`; oversized payloads receive `413`; rate limits
receive `429` with `Retry-After`; transient server failures receive `503` with
`Retry-After`. Clients may retry only bounded transient failures (at most three
attempts with backoff), reuse the same idempotency key, and otherwise retain a
visible pending/error state for user action. Never automatically overwrite after
a conflict or retry authorization failures.

### Message envelope

The signed-in API identity determines the authoritative `sourceSystem`; a message
whose declared source disagrees is rejected. For version 1 the allowed systems
are `NIKNOLIG` and `DEFENSE`; the target must be an explicitly authorized one of
those systems. `ELEKTRO_ENGEL` is a NIKNOLIG feature, not a separate identity or
trust domain.

Example proposal (digest shown as a placeholder):

```json
{
  "messageId": "8c21f98e-45f0-4b27-8917-d2da18bb1f86",
  "sourceSystem": "NIKNOLIG",
  "targetSystem": "NIKNOLIG",
  "messageType": "knowledge.proposal",
  "createdAt": "2026-10-12T22:00:00Z",
  "schemaVersion": 1,
  "content": {
    "kind": "general-information",
    "text": "Human-reviewed, non-sensitive knowledge proposal.",
    "source": {
      "title": "Source title",
      "url": "https://example.org/source",
      "retrievedAt": "2026-10-12T21:00:00Z",
      "license": "License and attribution details"
    }
  },
  "integrity": {
    "algorithm": "sha-256",
    "canonicalization": "RFC8785",
    "digest": "BASE64URL_DIGEST_OF_CANONICAL_ENVELOPE_WITHOUT_INTEGRITY"
  },
  "authorization": {
    "consent": true,
    "permission": "knowledge:submit-proposal"
  }
}
```

The API derives the authenticated principal and effective scopes from the
validated token; the `authorization` object is an auditable client assertion,
not proof of permission. The server recomputes the digest over the RFC 8785
canonicalized envelope with `integrity` omitted and rejects a mismatch. The
digest detects accidental or in-transit content changes but does not authenticate
the sender; TLS and server-verified identity provide that. Server receipt time,
review events, and monotonically increasing revision are authoritative and
append-only. The server validates UUID format, enum values, timestamp bounds,
maximum body size, HTTPS source URLs, authorization scopes, source/target pairing,
and digest before it persists a proposal. A proposal's `messageId` is its stable
idempotency identity and must equal the `Idempotency-Key` header; an exact retry
returns the original receipt, while reusing the key with different content is
rejected.

### Allowed content and conflict rules

The client-submitted envelope contains only:

- `messageId` and `schemaVersion`;
- `sourceSystem`, `targetSystem`, `messageType`, and client `createdAt`;
- an allow-listed `kind` and bounded, non-sensitive human-reviewed text;
- source metadata (`title`, canonical `url`, retrieval/revision date, license and
  attribution);
- `integrity` and the non-authoritative `authorization` consent/scope assertion
  shown in the envelope example.

The authenticated identity supplies the authoritative origin. Clients cannot
submit or change `updatedAt`, `reviewStatus`, reviewer identity, server receipt
time, or `revision`. The service creates those fields in its receipt and
append-only review history; it increments revisions on accepted edits and returns
the authoritative state on reads. Both client and server timestamps must be UTC
RFC 3339 values with bounded clock skew; server receipt time is authoritative for
ordering and retention.

The API rejects unknown fields, unsupported schema versions, disallowed
source/target pairs, malformed timestamps or URLs, oversized text/envelopes,
missing scopes/consent, and an invalid digest. Source URLs must use HTTPS and
must not embed credentials. Revision-precondition failures preserve both
versions for a human to reconcile; they never select a winner automatically.

Raw conversations, questions, interest profiles, device identifiers, credentials,
location, and operational/weapon-related content are out of scope. Proposals remain
untrusted and cannot change app behavior or safety rules until a human review
explicitly approves them.

Before implementation, the service owner must provision TLS hosting, identity
provider, per-client authorization/revocation, token lifetime/storage, request
size/rate limits, retention/deletion, audit access, and monitoring. The request
envelope is a contract draft only; no route other than `/v1/health` exists today.
