# Cloudflare assistant gateway

**Status: Worker code and mock tests exist; Cloudflare deployment and active Worker are not verified in this session. Neither client is connected.**

The Pages site is static and the existing Node service is health-only. The new
Worker is a separate server-side gateway; it preserves `GET /v1/health` and
provides `POST /v1/assistant/responses`. GitHub Pages and Android do not receive
or call the OpenAI credential.

## Components and boundaries

- `workers/assistant-worker.mjs` implements the Worker endpoint, Turnstile
  verification, Cloudflare Access JWT verification (RS256 signature, issuer,
  audience, expiry and not-before) from either a bearer token or Cloudflare's
  `Cf-Access-Jwt-Assertion` header, fixed OpenAI Responses request, generic
  failures, exact `https://nikolausnold.github.io` CORS policy, and input/output
  bounds.
- `workers/AssistantGuard` is a SQLite-backed Durable Object. It enforces a
  shared maximum of 100 requests reaching Turnstile verification per UTC day,
  12 per pseudonymous IP per day, 3 per IP per minute, and 2 simultaneous
  requests. IP addresses are HMAC-SHA-256 pseudonymized with a server secret
  before storage; IP-hash keys are removed by a daily alarm. The shared daily
  counter is a request-count ceiling, not a provider billing guarantee.
- `workers/assistant-worker.test.mjs` exercises the Worker with mocked external
  services; tests make no provider or Cloudflare requests.
- `workers/wrangler.jsonc` defines the Worker and Durable Object binding.

The endpoint requires a Cloudflare Access JWT signed by the configured team,
with matching issuer/audience and valid time claims. The caller must obtain a
short-lived JWT through an approved Cloudflare Access login flow. The Worker
also requires a single-use Turnstile token for the expected host and action.
Turnstile, distributed IP/global quotas and a concurrency cap are additional
abuse controls, **not user authentication**; CORS is also not authentication.
The endpoint is not suitable for sensitive or account-specific data. A daily
request cap bounds aggregate calls, while the fixed model and 300-token output
cap constrain per-call exposure. It does not establish a hard-dollar ceiling:
the operator must configure provider-side budgets/alerts and monitor usage
before enabling access. The Durable Object quotas apply after basic body
validation; configure Cloudflare edge rate limiting for all requests, including
malformed bodies and failed authentication/challenge checks.

Cloudflare access from this session is unavailable, so the user-provided
production script name and URL are not independently verified. Wrangler targets
the exact user-provided script slug `niknolig-defense` and enables its stated
URL `https://niknolig-defense.nikolausnold.workers.dev`; inspect the account
before any deployment. In
particular, this Worker does not serve static assets. If the active script
currently serves assets, deploying this code without preserving its asset
binding/files would replace that behavior.

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

Official references: [Wrangler installation](https://developers.cloudflare.com/workers/wrangler/install-and-update/),
[Worker deployment](https://developers.cloudflare.com/workers/wrangler/commands/#deploy),
[Worker routes](https://developers.cloudflare.com/workers/configuration/routing/routes/),
[custom domains](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/),
[Worker secrets](https://developers.cloudflare.com/workers/configuration/secrets/),
[Durable Objects](https://developers.cloudflare.com/durable-objects/),
[Access token validation](https://developers.cloudflare.com/cloudflare-one/access-controls/applications/http-apps/#token-validation),
[Turnstile server-side validation](https://developers.cloudflare.com/turnstile/get-started/server-side-validation/),
and the [OpenAI Responses API](https://platform.openai.com/docs/api-reference/responses).

1. Sign in to the existing Cloudflare account and inspect the existing Worker;
   do not create a second Worker. Confirm its script slug is exactly
   `niknolig-defense`, the account's workers.dev subdomain is `nikolausnold`,
   and the supplied URL matches. Review whether the active version serves
   static assets; this source contains no static asset fallback. Confirm Durable
   Objects SQLite availability and current plan/pricing before making changes.
2. Verify/reuse a Turnstile widget restricted to `nikolausnold.github.io`, using
   the managed challenge mode and action `niknolig_assistant`. Create a widget
   only if none exists and the authorized operator approves. Its site key is
   public but is not yet used by the website; its secret key is a Worker secret.
3. Create/verify a Cloudflare Access application/policy for the API and an
   approved user login flow. Configure the app's audience tag and team issuer
   domain; Cloudflare Access must inject its signed assertion or the approved
   client must obtain a short-lived JWT for that audience. A static shared
   gateway API key must not be embedded in either repository. The Worker
   validates the JWT cryptographically using the team's public JWKS endpoint.
   Confirm that Access can protect the chosen Worker hostname and that a
   browser-based Pages client can obtain/send the JWT; this has not been
   verified.
4. The Worker needs exactly three secret values:

   | Secret | Required | Purpose |
   | --- | --- | --- |
   | `OPENAI_API_KEY` | Yes | OpenAI credential, used only server-side. |
   | `TURNSTILE_SECRET_KEY` | Yes | Server-side verification of the planned Turnstile challenge. |
   | `RATE_LIMIT_HASH_SECRET` | Yes | At least 32 bytes of random entropy for HMAC pseudonymization of source IPs. |

   It does **not** use `GATEWAY_API_KEY`; a static key cannot safely authenticate
   a public Pages client. Configure these two non-secret Worker variables (not
   their secret values) from the Access application:

   | Variable | Purpose |
   | --- | --- |
   | `CF_ACCESS_TEAM_DOMAIN` | Access team hostname, e.g. `team.cloudflareaccess.com` (hostname only). |
   | `CF_ACCESS_AUD` | Audience tag for the specific Access application. |

   Do not commit either the OpenAI key, Turnstile secret, or HMAC secret.
   From a phone, use [Cloudflare Dashboard](https://dash.cloudflare.com/) →
   **Workers & Pages** → existing **niknolig-defense** → **Settings** →
   **Variables and Secrets**. Add the three secrets as encrypted Secret values
   and the two Access settings as plain-text Variables; enter values only in
   Cloudflare's secure form. Then use **Zero Trust** → **Access** →
   **Applications** to inspect/configure the matching Access app and allowed
   users. UI labels may vary; stop if the Worker slug, account hostname, active
   assets, or Access hostname/audience do not match. Saving settings can publish
   a Worker version; do this only after reviewing what will deploy.

### Smartphone check (inspection only; no deployment)

1. Open the [Cloudflare Dashboard](https://dash.cloudflare.com/) and select the
   account. Tap **Workers & Pages** → the existing **niknolig-defense** script
   → **Deployments** and **Settings**. Confirm the exact script slug, current
   deployment, `workers.dev` hostname, and whether assets are attached. Do not
   tap **Create application/Worker** or **Deploy**.
2. Open **Settings** → **Variables and Secrets** only to inspect whether the
   three required secrets and two Access variables exist. Never reveal, copy,
   screenshot, or send the secret values. This session cannot verify or set them.
3. Open **Zero Trust** → **Access** → **Applications** and inspect the existing
   app's hostname, audience, and allowed-user policy. Do not create an app or
   hostname on guesswork. If there is no matching Access app or the
   `workers.dev` hostname cannot be protected and used to obtain a JWT, stop;
   that needs an operator decision and an already-owned compatible hostname.
4. Check **Turnstile** for an existing widget restricted to
   `nikolausnold.github.io` and action `niknolig_assistant`. Do not display or
   share its secret key.

After checking, report only non-secret facts: script slug, whether the active
deployment serves assets, whether the endpoint hostname exists, and whether
matching Access/Turnstile configurations exist. Do not send credentials or
screenshots containing secret values.

### Deployment after review and approval

1. From the repository root, authenticate Wrangler in the approved operator
   environment, then upload the secret values through its interactive prompts.
   `keep_vars: true` is set to retain dashboard-managed non-secret variables,
   but verify them again after any deploy:

   ```sh
   npx wrangler login
   npx wrangler secret put OPENAI_API_KEY --config workers/wrangler.jsonc
   npx wrangler secret put TURNSTILE_SECRET_KEY --config workers/wrangler.jsonc
   npx wrangler secret put RATE_LIMIT_HASH_SECRET --config workers/wrangler.jsonc
   ```

   Generate `RATE_LIMIT_HASH_SECRET` with at least 32 random bytes in the
   operator's secret manager. Do not put secret values in shell arguments,
   command history, logs, Git, or local repo files. Create a restricted OpenAI
   project/key with least privilege and configure provider-side spend alerts.
2. Review the fixed model and request ceilings, legal/privacy notices, abuse
   response, and Cloudflare/OpenAI processing terms with the responsible
   operators. `store: false` is requested, but does not by itself establish
   provider zero-retention or legal compliance.
3. Only after verifying the existing target script and preserving any active
   assets, obtaining approval, and setting the secrets/variables, deploy
   explicitly. This will update the script named `niknolig-defense`; it must
   not create a second Worker:

   ```sh
   npx wrangler deploy --config workers/wrangler.jsonc
   ```

   Retain the exact website-origin allowlist in code. Do not deploy from an
   unreviewed branch. No remote state check or deployment occurred in this
   session.
4. Verify the deployment from an authorized environment: health response,
   Access JWT rejection/acceptance, correct preflight, denied foreign origins,
   missing-secret fail-closed behavior, invalid/replayed Turnstile rejection,
   per-IP and shared quota behavior, generic OpenAI errors, and provider-side
   spend monitoring. Never use a real OpenAI key or make a paid provider request
   in repository tests.

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
  Worker monitoring; configure edge rate-limiting for all endpoint attempts and
  validate Cloudflare Durable Object pricing and quota semantics for the
  selected account.
- Prove the website can obtain a short-lived Cloudflare Access JWT without
  exposing a client secret, and verify browser login/CORS behavior on the actual
  deployed hostname. The Worker accepts a bearer JWT or Cloudflare's signed
  assertion header but does not provide the sign-in UI or user-specific
  entitlements.
- Android remains local-only and is outside this website-only gateway release.

Run local tests without external services:

```sh
node --check workers/assistant-worker.mjs
node --test workers/assistant-worker.test.mjs
```

No Wrangler CLI, account, secret, route, or production environment is configured
in this repository. A successful local mock test is not evidence of a deployed
or production-ready AI integration.
