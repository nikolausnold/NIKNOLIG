# OpenAI assistant integration readiness

**Status: BLOCKED — no OpenAI requests are implemented or sent.**

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
- No OpenAI secret, model configuration, authentication issuer, hosting
  service, API gateway, budget, or deployment environment is configured.

The health server can be used as a starting point for a separately deployed API,
but the current health-only service is **not** a suitable production AI backend.
GitHub Pages must never call OpenAI directly or contain an OpenAI key. Do not
add an unauthenticated proxy endpoint to the present server: a public page's
origin and CORS policy do not authenticate its users or prevent scripted abuse.

## Target request flow

```text
Website / Android app
  └─ explicit AI-use choice + short-lived user access token
       └─ HTTPS API gateway / abuse controls
            └─ authenticated NIKNOLIG backend
                 └─ OpenAI Responses API (server-side key)
```

The existing local assistant remains available without network access. A user
must explicitly choose the AI option for each request; the app must not silently
fall back from local answers to a remote model.

## Proposed backend contract

Implement this only in an owned, deployed backend with the controls below.

### `POST /v1/assistant/responses`

- Require HTTPS and a short-lived user bearer token issued by the selected
  identity provider. Validate JWT signature against trusted issuer keys, issuer,
  audience, expiry, not-before, and the `assistant:ask` scope server-side.
  Website and Android clients must use a public-client flow such as OAuth
  Authorization Code with PKCE; no client secret belongs in either client.
- Bind authorization and quotas to the authenticated user/client. Apply
  distributed limits at the gateway and backend (per account and source IP),
  request concurrency limits, abuse monitoring, and a challenge or equivalent
  control for suspicious anonymous website traffic. CORS is only a browser
  policy, never an authorization substitute.
- Accept only a bounded JSON object such as
  `{"question":"...","locale":"de"}`. Reject unknown fields, empty questions,
  unsupported locales, control characters, and oversized bodies (suggested
  maximum 2 KiB and 1,000 Unicode characters). Do not accept a model, system
  prompt, tools, endpoint, token count, role, or conversation history from the
  client.
- Read the provider key and model allow-list from the server's protected secret
  and configuration stores. Call only the OpenAI Responses API from the server.
  Disable tools, browsing, file access, and function execution. Set a strict
  output-token limit, provider timeout, response-size limit, and `store: false`
  where supported by the selected API/model. Abort timed-out upstream work.
- Return only a bounded plain-text answer and a random request ID. Render the
  answer as text, not HTML. Do not relay provider headers, raw errors, or
  internal diagnostics to the client. Use generic `400`, `401`, `403`, `413`,
  `429`, and `503` responses, with `Retry-After` for throttling/transient
  failures.
- Never log questions, answers, access tokens, provider keys, or complete
  provider request/response bodies. Operational logs may include a random
  request ID, authenticated subject pseudonym, status, latency, and aggregate
  token/cost counters, subject to an approved retention period.

Do not persist full prompts, replies, or conversation history by default. If a
future feature needs history, it requires a separate product decision, explicit
user control, retention/deletion design, and a new privacy review.

### Abuse and cost controls

Before opening access, the service owner must configure per-user and per-IP
request/rate/concurrency quotas, provider organization/project separation,
model allow-list and spend alerts, an emergency kill switch, dashboards for
aggregate request/token/error rates, and an operational response plan. Provider
budget alerts may not be hard spending caps; independently enforce application
quotas and shut off the route when its budget is exhausted. Do not rely on a
single in-memory rate counter across replicas.

## Client disclosure and data minimization

Before the first remote AI request and at the point of use, clearly tell the
user that the specific question will be sent to NIKNOLIG's backend and OpenAI
to produce an answer, and link to the revised privacy notice. Require an
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

1. Select and provision an owned production backend, API gateway/WAF, TLS,
   identity provider, secret manager, distributed rate-limit/quota store,
   monitoring, and incident/cost shutoff. Do not use a GitHub Pages workflow as
   a runtime secret store or server.
2. Create a restricted OpenAI project/key for the backend only; set the model
   allow-list and provider data controls. Store the key in the hosting secret
   manager, rotate it, and prove it never enters build artifacts or logs.
3. Configure trusted issuer/audience/JWKS, PKCE clients, scopes, user/session
   revocation, exact website CORS origin, and Android redirect configuration.
   Configure CORS for the website only; native Android authorization relies on
   tokens, not CORS.
4. Approve request/response limits, quotas, cost ceiling, provider terms, data
   retention, privacy language, age/accessibility behavior, abuse handling, and
   safety policy with the responsible operators and qualified reviewers.
5. Implement the backend route in a separate, reviewable change. Add isolated
   provider mocks and tests for missing secrets, bad/expired/wrong-audience
   tokens, missing scopes, disallowed origins, invalid and oversized input,
   prompt/tool injection, provider timeouts/errors/malformed output, rate
   limits, quota exhaustion, redacted logs, and unchanged `/v1/health`.
6. Only after a production-like deployment and security/privacy approval, add
   the explicitly consented website and Android clients, update both privacy
   notices, test both clients on supported browsers/devices, and verify offline
   local assistance remains functional.
7. Treat launch as **BLOCKED** until all gates above are evidenced. A green
   unit-test run alone does not prove production controls or compliance.

## Current decision

No provider key, model choice, identity provider, hosting/gateway, spend policy,
or legal/privacy approval is configured. Therefore this change deliberately
does not add a client AI toggle, make external OpenAI calls, or claim that AI is
integrated. The safe next action is to provision and approve the backend
environment, then implement and independently review the server API before
enabling either client.
