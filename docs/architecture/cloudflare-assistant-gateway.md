# Cloudflare assistant gateway

**Status: implemented and tested locally; not deployed or connected to either client.**

The Pages site is static and the existing Node service is health-only. The new
Worker is a separate server-side gateway; it preserves `GET /v1/health` and
provides `POST /v1/assistant/responses`. GitHub Pages and Android do not receive
or call the OpenAI credential.

## Components and boundaries

- `workers/assistant-worker.mjs` implements the Worker endpoint, Turnstile
  verification, fixed OpenAI Responses request, generic failures, exact
  `https://nikolausnold.github.io` CORS policy, and input/output bounds.
- `workers/AssistantGuard` is a SQLite-backed Durable Object. It enforces a
  shared maximum of 100 requests reaching Turnstile verification per UTC day, 12 per pseudonymous IP
  per day, 3 per IP per minute, and 2 simultaneous requests. IP addresses are
  SHA-256 hashed with a server secret before storage; IP-hash keys are removed
  by a daily alarm. The shared daily counter is a request-count ceiling, not a
  provider billing guarantee.
- `workers/assistant-worker.test.mjs` exercises the Worker with mocked external
  services; tests make no provider or Cloudflare requests.
- `workers/wrangler.jsonc` defines the Worker and Durable Object binding.

The endpoint requires a valid, single-use Turnstile token for the expected host
and action. Turnstile plus distributed IP/global quotas and a concurrency cap
are abuse controls, **not user authentication**; CORS is also not
authentication. The endpoint is not suitable for sensitive or account-specific
data. A daily request cap bounds aggregate calls, while the fixed model and
300-token output cap constrain per-call exposure. It does not establish a
hard-dollar ceiling: the operator must configure provider-side budgets/alerts
and monitor usage before enabling access.

Each accepted body must contain exactly `question`, `locale`, `consent`, and
`turnstileToken`. The current question is the only user content sent to OpenAI;
history, tools, BLE/device data, saved interests, profile data, and client
model/system-prompt settings are rejected or never included. The server rejects
oversized/control-character input and obvious credential/payment patterns.
These heuristics cannot reliably detect every personal or sensitive detail.
Users must be warned before sending and must not submit sensitive information.
The Worker does not persist prompts or answers, and requests `store: false`.

## Cloudflare setup and deployment

Deployment is intentionally manual. Do not add production credentials to Git,
GitHub Pages variables, `.dev.vars`, examples, test fixtures, or command-line
arguments. The steps below require an authorized Cloudflare account and the
operator's explicit approval to deploy.

1. Create/select the Cloudflare account and Worker, and enable Workers Paid /
   Durable Objects SQLite as required by Cloudflare's current plan and product
   limits. Review current pricing, data location, retention, and service terms.
2. Create a Turnstile widget restricted to `nikolausnold.github.io`, using the
   managed challenge mode. The eventual website widget must set the action to
   `niknolig_assistant` (that client UI is not implemented yet).
   The site key is public but is not yet used by the website; the secret key is
   only a Worker secret.
3. From the repository root, authenticate Wrangler in the approved operator
   environment, then upload secrets through its interactive prompts:

   ```sh
   npx wrangler login
   npx wrangler secret put OPENAI_API_KEY --config workers/wrangler.jsonc
   npx wrangler secret put TURNSTILE_SECRET_KEY --config workers/wrangler.jsonc
   npx wrangler secret put RATE_LIMIT_HASH_SECRET --config workers/wrangler.jsonc
   ```

   Generate a unique high-entropy `RATE_LIMIT_HASH_SECRET` in the operator's
   secret manager. Do not put secret values in shell arguments, command history,
   logs, Git, or local repo files. Create a restricted OpenAI project/key with
   least privilege and configure provider-side spend alerts/limits.
4. Review the fixed model and request ceilings, legal/privacy notices, abuse
   response, and Cloudflare/OpenAI processing terms with the responsible
   operators. `store: false` is requested, but does not by itself establish
   provider zero-retention or legal compliance.
5. Only after approval, deploy explicitly:

   ```sh
   npx wrangler deploy --config workers/wrangler.jsonc
   ```

   The config initially exposes a `workers.dev` URL. Restrict production routing
   to the approved Worker hostname/domain and retain the exact website-origin
   allowlist in code. Do not deploy from an unreviewed branch. No deployment was
   performed as part of this change.
6. Verify the deployment from an authorized environment: health response,
   correct preflight, denied foreign origins, missing-secret fail-closed
   behavior, invalid/replayed Turnstile rejection, per-IP and shared quota
   behavior, generic OpenAI errors, and provider-side spend monitoring. Never
   use a real OpenAI key or make a paid provider request in repository tests.

## Still required before connecting the website

- Add an explicitly user-triggered AI mode to the website, with clear disclosure
  that the current question goes to Cloudflare/NIKNOLIG and OpenAI, local-only
  alternative, privacy link, sensitive-data warning, Turnstile widget, and
  answer rendering via `textContent`. The current `assistant.js` remains
  local-only and sends no question.
- Confirm Turnstile hostname/action behavior on the deployed Pages site and
  provide the public site key and Worker URL through reviewed public config.
- Update and review `privacy.html` for the real controller/operator, purposes,
  recipients, provider retention, international transfers, lawful basis, rights,
  age/accessibility behavior, and any required DPA/DPIA/legal review before
  production AI use.
- Set provider hard limits where available, alerts, incident response, and
  Worker monitoring; validate Cloudflare Durable Object pricing and quota
  semantics for the selected account.
- Consider a real identity provider if the feature becomes account-specific or
  must resist deliberate quota evasion. The current controls do not provide
  identity, user-specific entitlements, or proof that a human saw the consent UI.
- Android remains local-only and is outside this website-only gateway release.

Run local tests without external services:

```sh
node --check workers/assistant-worker.mjs
node --test workers/assistant-worker.test.mjs
```

No Wrangler CLI, account, secret, route, or production environment is configured
in this repository. A successful local mock test is not evidence of a deployed
or production-ready AI integration.
