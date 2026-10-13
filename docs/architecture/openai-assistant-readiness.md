# OpenAI assistant integration readiness

**Status: PARTIALLY IMPLEMENTED — Worker source and mock tests exist; it is not deployed, and neither client sends AI requests.**

This document records the safe integration boundary for the civilian NIKNOLIG
website and Android app. It is an implementation specification, not evidence of
a deployed backend, legal compliance, or a working AI feature. The separate
NIKNOLIG Defense repository could not be inspected and is not included in this
integration scope.

## What exists today

- `server/health-server.mjs` serves only the fixed, public `GET /v1/health`
  response. It has no identity, user-data route, provider client, persistent
  store, shared rate limiter, or production deployment configuration.
- The website is static GitHub Pages. `assistant.js` matches questions to
  bundled answers in the browser. `api-config.js` contains only a public base
  URL; it is not a secret or an authentication mechanism.
- The Android app matches questions to bundled resources locally. It has no
  assistant network client or sign-in flow.
- `workers/assistant-worker.mjs` now contains an isolated Cloudflare Worker with
  `POST /v1/assistant/responses`, the existing fixed `GET /v1/health` response,
  exact Pages-origin CORS, Cloudflare Access JWT verification, Turnstile
  verification, and a SQLite Durable Object enforcing shared daily, per-IP, and
  durable concurrency limits. Its tests use mocked external services only.
- `workers/wrangler.jsonc` targets the user-provided script slug
  `niknolig-defense` and its stated `workers.dev` hostname. Cloudflare access is
  not available in this session, so neither the account/script identity nor its
  active deployment/assets were independently verified. The repository contains
  no secret values or Access issuer/audience configuration. The Defense
  repository and its product policy were not inspected; this Worker still has
  the civilian Elektro-Engel prompt and is not approved for the Defense product.

The existing Node health server remains health-only and is not modified. The
Worker is gateway code in this repository, not evidence that the remote
`niknolig-defense` deployment contains or runs this code.
GitHub Pages must never call OpenAI directly or contain an OpenAI key. The
Worker now verifies Cloudflare Access JWT signature (RS256), issuer, audience,
expiry, and not-before claims before processing assistant requests. Turnstile,
quotas, and concurrency are additional abuse controls, not authentication; CORS
does not authenticate users. The Access login/application and client token flow
are not configured, and the endpoint must not be used for sensitive input.

## Target request flow

```text
Website (not connected)
  └─ explicit AI-use choice + Cloudflare Access JWT + Turnstile token
       └─ HTTPS Cloudflare Worker + Durable Object quotas
            └─ OpenAI Responses API (Worker secret)
```

The civilian website and Android assistants remain local and available without
network access. The website has no Turnstile widget, Access-token flow, consent
interface, or Worker URL configured and therefore does not invoke this endpoint.
Android is not connected.
Do not silently fall back from local answers to a remote model.

## Implemented Worker contract (undeployed)

The endpoint is implemented in `workers/assistant-worker.mjs` and described in
[`cloudflare-assistant-gateway.md`](cloudflare-assistant-gateway.md). It must not
be deployed or enabled until the remaining setup and release gates are approved.

### `POST /v1/assistant/responses`

- Require HTTPS, exact
  `Origin: https://nikolausnold.github.io`, and an `Authorization` bearer
  containing a Cloudflare Access JWT. Verify RS256 signature against the
  configured team's HTTPS JWKS, exact issuer and audience, expiry, not-before,
  and nonempty subject. Access is the authentication layer; a fixed-origin
  header is forgeable and is not authorization.
- Require a valid Turnstile token verified server-side for the expected hostname
  and action. This is additional abuse protection, not proof of identity or user
  consent. CORS is only a browser policy.
- A SQLite Durable Object enforces a shared maximum of 100 requests reaching
  Turnstile verification
  per UTC day, 12 requests per pseudonymous IP per day, 3 per IP per minute,
  and 2 concurrent upstream requests. Raw IPs are HMAC-SHA-256 pseudonymized
  using a server secret before persistence, and per-IP entries are cleared by a
  daily alarm. Quotas are fixed in Worker code and must be reviewed against
  actual provider pricing and Cloudflare plan limits. They cap request volume
  but do not establish a hard-dollar provider spend ceiling.
- Accept only the bounded JSON object
  `{"question":"...","locale":"de|en","consent":true,"turnstileToken":"..."}`.
  Reject unknown fields, an absent consent flag, empty/oversized questions,
  unsupported locales, control characters, sensitive-pattern matches, and
  request bodies over 8 KiB. Do not accept a model, system prompt, tools,
  endpoint, token count, role, or conversation history from the client.
- Read the provider key only from the Worker secret binding. The model is fixed
  in source, not client-selectable. Call only the OpenAI Responses API from the
  Worker, with no tools, browsing, file access, or function execution. The
  request caps output at 300 tokens, uses a 20-second provider timeout, limits
  the upstream response body to 64 KiB, and sets `store: false`; timed-out work
  is aborted.
- Return only a bounded plain-text answer and a random request ID. Render the
  answer as text, not HTML. Do not relay provider headers, raw errors, or
  internal diagnostics to the client. Use generic `400`, `403`, `413`, `415`,
  `429`, and `503` responses, with `Retry-After` for throttling/transient
  failures.
- The Worker emits no application logs and persists no prompts or answers.
  Configure and review Cloudflare platform/access-log fields and retention
  separately; never log questions, answers, access tokens, provider keys, or
  complete provider request/response bodies.

Do not persist full prompts, replies, or conversation history by default. If a
future feature needs history, it requires a separate product decision, explicit
user control, retention/deletion design, and a new privacy review.

### Additional abuse and cost controls required before deployment

The Worker implements Access JWT validation, fixed IP/global request quotas,
durable concurrency leases, a single-use Turnstile check, a fixed model, and a
300-token output ceiling.
The service owner must also configure Cloudflare edge rate limiting for all
requests, including malformed requests that do not reach the Durable Object.
Before deployment, provision an OpenAI project/key, provider spend controls and
alerts, an emergency shutoff, monitoring, an incident-response plan, and confirm
Cloudflare Durable Object pricing and limits. Provider budget alerts may not be
hard spending caps; the request quota is not a dollar quota. Deliberate quota
evasion, shared IPs, and quota exhaustion remain possible. Add a real identity
provider if account-bound quotas or entitlements are required.

## Client disclosure and data minimization

Before the first remote AI request and at the point of use, clearly tell the
user that the specific question will pass through Cloudflare/NIKNOLIG's backend
and be sent to OpenAI to produce an answer. Turnstile also receives the
challenge token and source IP. Link to the revised privacy notice. Require an
affirmative, unbundled action to send; provide a local-only alternative.

Transmit only the current question and a supported locale. Do not send saved
interest categories, prior questions/answers, BLE advertisements or device
addresses, location, account profile fields, or app diagnostics. Warn users not
to enter passwords, API keys, payment details, health data, or other sensitive
information; reject obvious credential/payment patterns where feasible, while
making clear that automated filtering cannot guarantee removal of personal
data.

The existing website and Android privacy notices describe local-only question
handling. They must be updated and reviewed **before** enabling remote requests;
the notice must identify purposes, controller/operator, OpenAI as recipient,
retention settings, international transfers, user rights, and relevant lawful
basis. The service owner must obtain appropriate provider terms/DPA, assess
transfer mechanisms and any required DPIA, determine age restrictions, and
obtain qualified review of GDPR and EU AI Act duties for the actual deployment.
This document makes no legal-compliance determination.

## Safety and product boundaries

The assistant remains civilian and informational. Preserve existing refusals
for electrical work, repairs, bypassing safeguards, and urgent hazards. Do not
provide hazardous operational instructions or allow user input/model output to
change app permissions, safety policy, BLE behavior, device trust, or controls.
Present output as AI-generated and fallible, direct emergencies to appropriate
human/emergency services, and give a practical way to report unsafe answers.
These controls require threat modeling and adversarial evaluation; a prompt alone
is not a security boundary.

## Required environment and release gates

1. Provision and authorize the Cloudflare Worker, Durable Object SQLite
   namespace, Turnstile widget, secret bindings, approved hostname/routing,
   monitoring, and incident/cost shutoff. Worker source and Wrangler binding
   config are present; account resources are not.
2. Create a restricted OpenAI project/key for the Worker only; review model and
   provider data controls. Store the key in Cloudflare's secret manager, rotate
   it, and prove it never enters build artifacts or logs.
3. Configure the Cloudflare Access application, team domain, audience, allowed
   users, and a browser login/token flow that works with the deployed API
   hostname. Set `CF_ACCESS_TEAM_DOMAIN` and `CF_ACCESS_AUD` as non-secret
   Worker variables. Separately configure Turnstile for the exact Pages hostname
   and action, and keep the Worker CORS origin exact. Android remains
   disconnected.
4. Approve request/response limits, quotas, cost ceiling, provider terms, data
   retention, privacy language, age/accessibility behavior, abuse handling, and
   safety policy with the responsible operators and qualified reviewers.
5. The Worker route and dependency-free mocked tests are implemented. Before
   deployment, independently review the Worker and verify missing-secret
   fail-closed behavior, denied origins, invalid/replayed Turnstile, input and
   output bounds, provider timeout/error/malformed output, quotas, concurrency,
   cost controls, and unchanged `/v1/health`. Never use a live key or paid
   provider request in tests.
6. Only after a production-like deployment and security/privacy approval, add
   the explicitly consented website and Android clients, update both privacy
   notices, test both clients on supported browsers/devices, and verify offline
   local assistance remains functional.
7. Treat launch as **BLOCKED** until all gates above are evidenced. A green
   unit-test run alone does not prove production controls or compliance.

## Current decision

The Worker gateway code, fixed model, Access JWT verification, and mock test
suite are present. Cloudflare's remote state cannot be checked from this
session; no secrets or Access settings are present in repository config, and
there is no deployment verification, client connection, or legal/privacy
approval. Product clients still make no OpenAI calls. The source currently
describes the civilian Elektro-Engel assistant and must not be deployed to the
Defense product until its policy, hostname/assets, and Access application are
confirmed. The authorized operator must inspect the current Worker first; only
after preserving its existing behavior and obtaining approvals may the
appropriate client be connected.
