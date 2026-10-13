# OpenAI assistant integration readiness

**Status: Central gateway and consent-gated website client implemented and locally mock-tested; production use is BLOCKED. No deployment was made.**

## Verified repository state

- `server/health-server.mjs` remains a separate health-only Node service. It has
  no user-data routes, authentication, provider client, or persistent store.
- The website `assistant.js` keeps its local answer path and has a separate,
  explicitly consented cloud request path. It requires public Pages
  endpoint/site-key configuration and an authenticated Cloudflare Access browser
  session; these are not configured or live-verified. Android remains local and
  offline-first with no cloud client.
- `workers/assistant-worker.mjs` implements the central multi-application
  Cloudflare Worker API. It preserves `GET /v1/health`, provides canonical
  `POST /v1/ai/responses` and retains `/v1/assistant/responses` as a compatible
  alias. App access is derived from a verified single-audience Access JWT and an
  operator-managed `AI_CLIENTS_JSON` mapping; clients cannot choose their
  application profile or model.
- Profiles (`niknolig`, `elektro-engel`, `defense`) are server-defined. Each
  mapped app has its own model/origin/action and per-app quota; no chat history,
  application data, tools, or arbitrary URL fetches are accepted. The Defense
  profile is only a conservative civilian-information baseline, not an
  approved Defense product policy or access to Defense data.
- `workers/wrangler.jsonc` now names the target `niknolig`, based on the
  user-provided description of the existing central Worker. This is only local
  configuration: the Cloudflare account, live script, route, assets, and
  Durable Object were not independently inspected. The public Defense
  repository lookup returned no matching public repository, so its policy and
  implementation were not reviewed.
- The repository also retains `workers/assistant-worker.test.mjs`, which mocks
  Cloudflare Access, Turnstile, the Durable Object, and OpenAI. No real key or
  paid provider request is used.

## API and safeguards

The API requires a Cloudflare Access JWT (RS256 signature, issuer, single
audience, expiry/not-before and subject). The configured audience maps to one
approved `clientId`, server-defined profile, model allowlist, exact HTTPS
origins, and output ceiling. Browser requests require exact configured Origin
and server-verified Turnstile; native requests require an audience configured
with no browser origins and omit Turnstile. CORS is not authentication.

The body contains only the current `question`, `locale`, affirmative `consent`,
and (for browsers) a Turnstile token. Unknown fields/history are rejected.
OpenAI receives only that question, a server-side profile instruction, and
locale. The call uses an operator-selected allowlisted model, `store: false`,
no tools, 300 output tokens maximum, 20-second timeout, fixed provider URL, and
bounded response parsing. Errors do not relay provider diagnostics.

The Durable Object applies a shared 100-request daily ceiling, 50 per client per
day, 12 per client/IP/day, 3 per client/IP/minute, and two concurrent requests.
IP hashes are HMAC-pseudonymized. These limits and provider budgets are not a
hard-dollar cap; edge rate limiting, provider-side spend controls/alerts,
monitoring, and shutoff remain operator requirements.

The Worker does not persist questions or answers. `store: false` is a provider
request setting, not proof of zero retention or regulatory compliance. Input
filters cannot guarantee sensitive-data removal.

## Launch gates still open

1. An authorized operator must inspect the existing `niknolig` Worker, script
   code, assets, routes/domain, Durable Object state, account plan, and current
   functions. The repository contains no static assets. Do not deploy if the
   live Worker has behavior that this code would overwrite.
2. Configure the Access team and a separate approved audience/identity policy
   for each client; set encrypted `OPENAI_API_KEY` and 32-byte
   `RATE_LIMIT_HASH_SECRET`, plus `CF_ACCESS_TEAM_DOMAIN` and validated
   `AI_CLIENTS_JSON`. Browser clients additionally need `TURNSTILE_SECRET_KEY`.
   None of these production settings are available or verified here.
3. Inspect the actual NIKNOLIG Defense repository and policy before enabling its
   separate audience. The user-described Defense Worker was not accessed. Do not
   reuse Defense data or authorization in general NIKNOLIG or Elektro-Engel.
4. Review product terms, spend limits, edge limits, incident/shutoff process,
   provider data controls, controller/processor responsibilities, retention,
   lawful basis, international transfers, user rights, age/accessibility
   behavior, and any required GDPR/EU AI Act review with responsible reviewers.
   No compliance determination is made here.
5. Website UI now has point-of-use disclosure/consent, lazy Turnstile, explicit
   submit, and retains the local answer path. Configure/test Access browser
   cookies, preflight, Turnstile and public Pages settings on staging before
   enabling it. Preserve local answers and offline operation; remote AI must
   remain user-triggered and must not silently replace local behavior.
6. Obtain explicit deployment approval, deploy only after verifying the existing
   Worker target and preserving its behavior, then perform authorized staging
   checks. This task performed no Cloudflare deployment, secret configuration,
   live OpenAI call, or Android client integration.

See [`cloudflare-assistant-gateway.md`](cloudflare-assistant-gateway.md) for the
config schema, secret setup, limits, and deployment checklist.
