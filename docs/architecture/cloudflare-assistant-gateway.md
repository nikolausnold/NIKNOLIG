# Central Cloudflare AI gateway

**Status: Implemented and mock-tested locally; not deployed. No application client is connected.**

The existing Worker source in `workers/assistant-worker.mjs` provides the
platform's shared, server-side OpenAI integration. `workers/wrangler.jsonc`
targets the user-identified existing Worker slug `niknolig`; it does not prove
that the live script, routes, or assets match. Cloudflare account state was not
available for inspection. The Defense repository could not be found publicly,
so its integration policy and current Worker were not verified. Do not deploy
until an authorized operator confirms the target and preserves any existing
behavior/assets.

## Versioned API and authorization

- `GET /v1/health` retains the existing public health response.
- `POST /v1/ai/responses` is the canonical API. The existing
  `/v1/assistant/responses` path remains an alias for compatibility.
- A request requires a Cloudflare Access JWT signed by the configured team
  (`RS256`, exact issuer, valid time claims, nonempty subject) via `Authorization:
  Bearer` or `Cf-Access-Jwt-Assertion`. Its single audience must match exactly
  one server-configured application. Missing/invalid configuration fails closed.
- `AI_CLIENTS_JSON` is a non-secret Cloudflare Worker variable containing the
  operator-managed authorization map. Each entry binds one unique Access
  audience to one application ID, fixed profile, allowed model, exact HTTPS
  browser origins, Turnstile action if browser origins are configured, and an
  optional output-token ceiling:

  ```json
  [
    {
      "audience": "<Access audience>",
      "clientId": "niknolig-web",
      "profile": "niknolig",
      "model": "gpt-4.1-mini",
      "origins": ["https://nikolausnold.github.io"],
      "turnstileAction": "niknolig_assistant",
      "maxOutputTokens": 300
    },
    {
      "audience": "<different Access audience>",
      "clientId": "elektro-engel",
      "profile": "elektro-engel",
      "model": "gpt-4.1-nano",
      "origins": ["https://nikolausnold.github.io"],
      "turnstileAction": "elektro_engel"
    },
    {
      "audience": "<different Access audience>",
      "clientId": "niknolig-defense",
      "profile": "defense",
      "model": "gpt-4.1-mini",
      "origins": ["<approved Defense HTTPS origin>"],
      "turnstileAction": "defense_assistant"
    },
    {
      "audience": "<different Access audience>",
      "clientId": "niknolig-android",
      "profile": "niknolig",
      "model": "gpt-4.1-nano",
      "origins": []
    }
  ]
  ```

  This is a schema example, not a live configuration. Use real audience tags
  obtained from separate approved Access applications. Do not configure the
  Defense entry until its repository, policy, origin, Access application, and
  data boundaries have been reviewed. The supported profiles are fixed in
  Worker code (`niknolig`, `elektro-engel`, `defense`); model selection is
  operator-controlled and limited to `gpt-4.1-mini` or `gpt-4.1-nano`. Clients
  cannot choose a profile, model, origin, or capability in their request.
- Browser clients must send an allowed `Origin` and a Turnstile token; the
  Worker verifies Turnstile server-side against the origin hostname and that
  client's configured action. A native client has no `Origin`, must map to an
  entry with `origins: []`, and does not use Turnstile. This does not mean an
  Android sign-in/token flow is implemented.
- `Origin`/CORS is browser policy only, not authentication. Access JWT audience
  is the application authorization boundary. The audience-to-profile mapping
  and quotas are held server-side; no user content, conversation history, files,
  tools, BLE/device data, or profile data is shared between applications.

The JSON request body must contain exactly:

- Browser: `{"question":"...","locale":"de|en","consent":true,"turnstileToken":"..."}`
- Native: `{"question":"...","locale":"de|en","consent":true}`

Unknown fields (including `app`, `profile`, `model`, `systemPrompt`, `tools`,
and conversation history) are rejected. Only the current question and locale
are sent to OpenAI. The Worker uses a fixed OpenAI endpoint, `store: false`, no
tools, a 20-second timeout, a 64-KiB upstream response limit, and at most 300
output tokens (or a lower configured client limit). Input, output, and provider
errors are bounded and provider diagnostics are not returned.

## Abuse protection and privacy

- A SQLite Durable Object enforces 100 requests/platform/day, 50/client/day,
  12/client-and-pseudonymous-IP/day, 3/client-and-IP/minute, and 2 simultaneous
  requests. IPs are HMAC-SHA-256 pseudonymized before storage; per-IP keys are
  cleared by an alarm. The shared platform ceiling is intentional cost
  containment, so apps can still compete for that total. These request quotas
  do not establish a hard-dollar spend limit.
- Configure edge rate limiting for all requests, OpenAI budget/usage alerts and
  a shutoff. Review Durable Object limits/pricing and provider terms.
- The Worker does not persist questions/answers or log application content.
  The quota object stores counters and pseudonymous IP hashes. `store: false`
  requests non-storage from the API but does not establish provider zero
  retention or legal compliance.
- The required explicit consent flag is an API contract, not a user-consent UI.
  Before connecting a client, disclose that the question is sent via Cloudflare
  to OpenAI, provide a local-only option, update the privacy notice, and obtain
  the required privacy/legal review. Filtering obvious secrets/payment data is
  best effort, not a guarantee; do not send sensitive data by default.
- The Defense profile is a separate, conservative civilian-information prompt,
  not authorization to access Defense data or a substitute for Defense policy
  review. No database, tools, shared transcript, or external URL retrieval is
  available to any profile.

## Cloudflare configuration and release gate

Only an authorized operator should configure/deploy this Worker. Use the
[Cloudflare Dashboard](https://dash.cloudflare.com/) to inspect the existing
`niknolig` Worker first. Confirm its script slug, current code, routes/custom
domain, assets, Durable Object state, settings, and existing health/function
behavior. This repository has no static-asset binding. If that Worker serves
assets or other functions, stop and design a preservation/merge plan before
deployment; do not create a duplicate Worker or overwrite behavior by guesswork.

Required encrypted Worker secrets:

| Secret | Required when | Purpose |
| --- | --- | --- |
| `OPENAI_API_KEY` | AI requests enabled | Server-side OpenAI credential. |
| `RATE_LIMIT_HASH_SECRET` | AI requests enabled | At least 32 bytes of random entropy for IP pseudonymization. |
| `TURNSTILE_SECRET_KEY` | Any browser client is configured | Server-side Turnstile verification. Native-only mappings do not need it. |

Required non-secret Worker variables:

| Variable | Purpose |
| --- | --- |
| `CF_ACCESS_TEAM_DOMAIN` | Lowercase Cloudflare Access team hostname; issuer and JWKS host. |
| `AI_CLIENTS_JSON` | Validated per-application audience/profile/model/origin map above. |

Never store secrets in Git, GitHub Pages, the Android app, `.dev.vars`, sample
files, command arguments, or logs. `GATEWAY_API_KEY` is not used; a static shared
key embedded in public clients would not authenticate an application.

Before deployment, the operator must:

1. Verify the `niknolig` Worker is the existing central script and determine how
   to preserve any active assets/functions. Verify the actual routes and plan;
   the local Wrangler config is not proof of remote state.
2. Create/verify a separate Access application/audience for every allowed
   product. Verify the approved users/identity flow, issuer, JWT delivery, and
   origin. Do not expose a client secret in a public app.
3. Review each profile and its audience mapping. Do not activate Defense until
   the separate Defense repository and policy are reviewed.
4. Set encrypted secrets and the two plain-text variables in Cloudflare; apply
   edge limits and provider budgets/alerts; review processing, retention,
   privacy notices, and release approvals.
5. Only with explicit approval, use the repository's Wrangler config and deploy
   after reviewing the resulting diff/asset behavior. No live deployment or
   paid OpenAI call has been performed in this task.
6. Verify health preservation, Access rejection/acceptance, each app's denied
   cross-origin/audience cases, Turnstile, quota limits, secret failures,
   provider errors, and monitoring from an authorized environment.

Local mock checks (no Cloudflare account or paid API):

```sh
node --check workers/assistant-worker.mjs
node --test workers/assistant-worker.test.mjs
```

## Client status

Website `assistant.js` remains local-only. Android assistance remains
offline-first and has no Access login/network client. Neither application sends
requests to this Worker. Connecting either client requires a reviewed identity
flow, explicit point-of-use consent/disclosure, privacy updates, product tests,
and a deployment approval; no client silently falls back to cloud.
