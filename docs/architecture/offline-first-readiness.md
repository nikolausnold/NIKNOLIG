# Offline-first readiness and integration boundary

This document describes the checked-out Android prototype and a future design.
It does not claim release readiness, a running sync service, or interoperability
with the separate `-NIKNOLIG-Defense` repository.

## Current inventory

| Area | Status | Evidence / boundary |
| --- | --- | --- |
| Android structure | PRESENT | Native `MainActivity` and `ElektroEngelActivity`; XML layouts and localized Android resources. |
| Offline assistance | PARTIAL | Scenario text and keyword-matched answers are bundled with the app. No generative model or remote dependency is required for them. |
| Local preferences | PARTIAL | The optional, general-interest categories are stored in app-private `SharedPreferences` after consent and can be cleared. They are not a sync queue or encrypted database. |
| Internet status | PRESENT | The app observes Android's default network and distinguishes offline, local-only, unverified, validated internet, and unknown. Validated internet does not prove that the NIKNOLIG API is reachable. |
| Server status | PRESENT, OPTIONAL | A user-started HTTPS `GET /v1/health` check is available when a base URL is configured. It sends no question or interest data. |
| BLE | PARTIAL | Foreground, user-started discovery displays advertised name and signal strength for up to 30 seconds. Results are transient and cleared on leaving the Activity. |
| Device connection / GATT | MISSING | There is no connection manager, device authentication, GATT read/write, trust decision, or device control in this checkout. |
| Durable offline outbox / sync | MISSING | No queued mutations, sync worker, conflict model, provenance tracking, or retry state exists. |
| Website offline behavior | NOT GUARANTEED | The site is static but has no offline cache/service worker in this checkout. |
| API for knowledge exchange | BLOCKED | `server/health-server.mjs` exposes only `/v1/health`; there is no backend contract or verified Defense API. |
| CI | PRESENT | Android build/test and website deploy workflows exist. The latest observed `main` Android and website runs succeeded; release signing and publication still require their configured approvals/secrets and separate review. |

## Connectivity status semantics

The live UI status is based on Android `ConnectivityManager` network capability
signals while each Activity is visible. It is a reachability hint, not a server
health check and not a guarantee that every request will succeed. The BLE search
and bundled assistant remain local functions when the internet is unavailable.
The monitor unregisters with the Activity lifecycle and does not start background
polling or transmit application data.

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

## Proposed knowledge-exchange contract (not implemented)

NIKNOLIG and Defense remain separate applications and repositories. Any future
exchange uses a narrowly scoped HTTPS service; neither app gets direct access to
the other's local database or trust zone.

An explicitly submitted knowledge proposal may contain only:

- `id` and `schemaVersion`;
- an allow-listed `kind` and bounded, non-sensitive human-reviewed text;
- `origin` (`NIKNOLIG` or `DEFENSE`) and `createdAt` / `updatedAt` UTC timestamps;
- `source` metadata (`title`, canonical `url`, retrieval/revision date, license and
  attribution);
- `reviewStatus` (`pending`, `approved`, or `rejected`), reviewer identity and
  review timestamp when applicable;
- a monotonically increasing `revision`.

Raw conversations, questions, interest profiles, device identifiers, credentials,
location, and operational/weapon-related content are out of scope. Proposals remain
untrusted and cannot change app behavior or safety rules until a human review
explicitly approves them.

Candidate operations are an authenticated read of approved records and an
authenticated, user-confirmed proposal submission. Every retry must be idempotent.
Updates use a revision precondition (for example, `If-Match` with an ETag); a
revision mismatch returns a conflict and preserves both versions for review rather
than silently overwriting. Records retain source, origin, timestamps, review
history, and deletion/revocation state.

Before implementation, the service owner must specify TLS hosting, per-client
authorization and revocation, token lifetime/storage, request size/rate limits,
retention/deletion, audit access, and failure handling. No API key or long-lived
secret may be embedded in either application, website, or repository. Authenticated
transport, local outbox encryption, and identity provisioning are design
requirements, not existing functionality.
