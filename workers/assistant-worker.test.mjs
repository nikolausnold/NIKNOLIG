import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import worker, { AssistantGuard } from "./assistant-worker.mjs";

const ORIGIN = "https://nikolausnold.github.io";
const ACCESS_TEAM_DOMAIN = "team.example.cloudflareaccess.com";
const ACCESS_AUDIENCE = "test-application-audience";
const NIKNOLIG_AUDIENCE = "test-niknolig-audience";
const DEFENSE_AUDIENCE = "test-defense-audience";
const NATIVE_AUDIENCE = "test-native-audience";
const DEFENSE_ORIGIN = "https://defense.example";
const CLIENTS = [
    {
        audience: ACCESS_AUDIENCE,
        clientId: "elektro-engel",
        profile: "elektro-engel",
        model: "gpt-4.1-mini",
        origins: [ORIGIN],
        turnstileAction: "niknolig_assistant"
    },
    {
        audience: NIKNOLIG_AUDIENCE,
        clientId: "niknolig-general",
        profile: "niknolig",
        model: "gpt-4.1-nano",
        origins: [ORIGIN],
        turnstileAction: "niknolig_assistant",
        maxOutputTokens: 120
    },
    {
        audience: DEFENSE_AUDIENCE,
        clientId: "niknolig-defense",
        profile: "defense",
        model: "gpt-4.1-mini",
        origins: [DEFENSE_ORIGIN],
        turnstileAction: "defense_assistant"
    },
    {
        audience: NATIVE_AUDIENCE,
        clientId: "niknolig-android",
        profile: "niknolig",
        model: "gpt-4.1-nano",
        origins: [],
        maxOutputTokens: 120
    }
];
const { privateKey: accessPrivateKey, publicKey: accessPublicKey } = await crypto.subtle.generateKey(
    {
        name: "RSASSA-PKCS1-v1_5",
        modulusLength: 2_048,
        publicExponent: new Uint8Array([1, 0, 1]),
        hash: "SHA-256"
    },
    true,
    ["sign", "verify"]
);
const accessJwk = {
    ...(await crypto.subtle.exportKey("jwk", accessPublicKey)),
    kid: "test-access-key",
    alg: "RS256",
    use: "sig"
};
const cleanups = [];

function encodeBase64Url(bytes) {
    let binary = "";
    for (const byte of bytes) binary += String.fromCharCode(byte);
    return btoa(binary).replace(/=/g, "").replace(/\+/g, "-").replace(/\//g, "_");
}

async function createAccessToken({
    audience = ACCESS_AUDIENCE,
    issuer = `https://${ACCESS_TEAM_DOMAIN}`,
    expiresAt = Math.floor(Date.now() / 1_000) + 300,
    notBefore,
    kid = accessJwk.kid
} = {}) {
    const encodeJson = (value) => encodeBase64Url(new TextEncoder().encode(JSON.stringify(value)));
    const segments = [
        encodeJson({ alg: "RS256", typ: "JWT", kid }),
        encodeJson({
            iss: issuer,
            aud: audience,
            sub: "test-user",
            exp: expiresAt,
            ...(notBefore === undefined ? {} : { nbf: notBefore })
        })
    ];
    const signed = new TextEncoder().encode(segments.join("."));
    const signature = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", accessPrivateKey, signed);
    return `${segments.join(".")}.${encodeBase64Url(new Uint8Array(signature))}`;
}

const validAccessToken = await createAccessToken();

class MemoryStorage {
    constructor() {
        this.values = new Map();
        this.alarm = null;
        this.tail = Promise.resolve();
    }

    get(key) {
        return Promise.resolve(this.values.get(key));
    }

    put(key, value) {
        this.values.set(key, structuredClone(value));
        return Promise.resolve();
    }

    delete(key) {
        return Promise.resolve(this.values.delete(key));
    }

    getAlarm() {
        return Promise.resolve(this.alarm);
    }

    setAlarm(timestamp) {
        this.alarm = timestamp;
        return Promise.resolve();
    }

    list({ prefix }) {
        return new Map(
            [...this.values].filter(([key]) => key.startsWith(prefix))
        );
    }

    transaction(callback) {
        const result = this.tail.then(() => callback(this));
        this.tail = result.catch(() => {});
        return result;
    }
}

class FakeGuardBinding {
    constructor() {
        this.storage = new MemoryStorage();
        this.instance = new AssistantGuard({ storage: this.storage });
    }

    idFromName(name) {
        return name;
    }

    get() {
        return {
            fetch: (input, init) => this.instance.fetch(
                input instanceof Request ? input : new Request(input, init)
            )
        };
    }
}

function makeEnvironment(overrides = {}) {
    return {
        OPENAI_API_KEY: "mocked-only",
        TURNSTILE_SECRET_KEY: "mocked-only",
        RATE_LIMIT_HASH_SECRET: "m".repeat(32),
        CF_ACCESS_TEAM_DOMAIN: ACCESS_TEAM_DOMAIN,
        AI_CLIENTS_JSON: JSON.stringify(CLIENTS),
        ASSISTANT_GUARD: new FakeGuardBinding(),
        ...overrides
    };
}

function mockExternalServices({
    turnstile = true,
    provider = true,
    turnstileHostname = "nikolausnold.github.io",
    turnstileAction = "niknolig_assistant"
} = {}) {
    const calls = [];
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url, options = {}) => {
        calls.push({ url: String(url), options });
        if (String(url).includes("challenges.cloudflare.com")) {
            const body = new URLSearchParams(options.body);
            const valid = turnstile && body.get("response") === "valid-turnstile-token";
            return Response.json({
                success: valid,
                hostname: valid ? turnstileHostname : "invalid.example",
                action: valid ? turnstileAction : "other"
            });

            test("rejects a valid application's token when used with another application's origin", async () => {
                const calls = mockExternalServices();
                const response = await worker.fetch(makeRequest({
                    origin: ORIGIN,
                    accessToken: await createAccessToken({ audience: DEFENSE_AUDIENCE })
                }), makeEnvironment());
                assert.equal(response.status, 403);
                assert.equal(calls.some(({ url }) => url.includes("api.openai.com")), false);
            });
        }
        if (String(url) === `https://${ACCESS_TEAM_DOMAIN}/cdn-cgi/access/certs`) {
            return Response.json({ keys: [accessJwk] });
        }
        if (String(url) === "https://api.openai.com/v1/responses") {
            if (provider === "timeout") throw new Error("mocked timeout");
            if (provider === "malformed") return Response.json({ output: [] });
            return provider
                ? Response.json({
                    output: [{
                        type: "message",
                        content: [{ type: "output_text", text: "Eine allgemeine Antwort." }]
                    }]
                })
                : Response.json({ error: { message: "private upstream detail" } }, { status: 500 });
        }
        throw new Error("Unexpected external request");
    };
    cleanups.push(() => {
        globalThis.fetch = originalFetch;
    });

    return calls;
}

function makeRequest({
    question = "Wie plane ich meinen Tag?",
    locale = "de",
    consent = true,
    turnstileToken = "valid-turnstile-token",
    origin = ORIGIN,
    ip = "203.0.113.10",
    accessToken = validAccessToken,
    accessHeader = false,
    path = "/v1/ai/responses",
    body,
    contentType = "application/json"
} = {}) {
    const payload = body ?? JSON.stringify({
        question,
        locale,
        consent,
        ...(origin ? { turnstileToken } : {})
    });
    return new Request(`https://gateway.example${path}`, {
        method: "POST",
        headers: {
            ...(origin ? { Origin: origin } : {}),
            "CF-Connecting-IP": ip,
            "Content-Type": contentType,
            ...(accessToken ? {
                [accessHeader ? "Cf-Access-Jwt-Assertion" : "Authorization"]:
                    accessHeader ? accessToken : ["Bear", "er "].join("") + accessToken
            } : {})
        },
        body: payload
    });
}

async function assertRateLimited(response) {
    const result = await response.json();
    assert.equal(result.allowed, false);
    assert.ok(result.retryAfter > 0);
}

afterEach(() => {
    while (cleanups.length) cleanups.pop()();
});

test("keeps the public health endpoint and allows only configured CORS origins", async () => {
    const health = await worker.fetch(new Request("https://gateway.example/v1/health", {
        headers: { Origin: ORIGIN }
    }), makeEnvironment());
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), {
        status: "ok",
        service: "niknolig-api",
        apiVersion: 1
    });
    assert.equal(health.headers.get("access-control-allow-origin"), ORIGIN);

    const denied = await worker.fetch(new Request("https://gateway.example/v1/ai/responses", {
        method: "OPTIONS",
        headers: { Origin: "https://attacker.example" }
    }), makeEnvironment());
    assert.equal(denied.status, 403);
    assert.equal(denied.headers.get("access-control-allow-origin"), null);
});

test("fails closed when any server secret or quota binding is missing", async () => {
    const calls = mockExternalServices();
    const missingConfiguration = [
        { OPENAI_API_KEY: "" },
        { TURNSTILE_SECRET_KEY: "" },
        { RATE_LIMIT_HASH_SECRET: "too-short" },
        { RATE_LIMIT_HASH_SECRET: "" },
        { AI_CLIENTS_JSON: "" },
        { CF_ACCESS_TEAM_DOMAIN: "" },
        { ASSISTANT_GUARD: undefined }
    ];
    for (const overrides of missingConfiguration) {
        const response = await worker.fetch(makeRequest(), makeEnvironment(overrides));
        assert.equal(response.status, 503);
    }
    assert.equal(calls.length, 0);
});

test("requires a valid Cloudflare Access JWT with trusted issuer, audience, signature, and expiry", async () => {
    const calls = mockExternalServices();
    const invalidTokens = [
        "",
        "not-a-jwt",
        await createAccessToken({ audience: "wrong-application-audience" }),
        await createAccessToken({ audience: [ACCESS_AUDIENCE, DEFENSE_AUDIENCE] }),
        await createAccessToken({ issuer: "https://attacker.example" }),
        await createAccessToken({ expiresAt: Math.floor(Date.now() / 1_000) - 1 }),
        await createAccessToken({ notBefore: Math.floor(Date.now() / 1_000) + 120 }),
        `${validAccessToken.split(".").slice(0, 2).join(".")}.${"A".repeat(342)}`
    ];
    for (const accessToken of invalidTokens) {
        const response = await worker.fetch(makeRequest({ accessToken }), makeEnvironment());
        assert.equal(response.status, 401);
        assert.deepEqual(await response.json(), { error: "unauthorized" });
        assert.equal(response.headers.get("www-authenticate"), "Bearer");
    }

    const missingConfiguration = await worker.fetch(
        makeRequest(),
        makeEnvironment({ AI_CLIENTS_JSON: JSON.stringify(CLIENTS.slice(1)) })
    );
    assert.equal(missingConfiguration.status, 401);
    assert.equal(calls.some(({ url }) => url.includes("api.openai.com")), false);
    assert.equal(calls.some(({ url }) => url.includes("challenges.cloudflare.com")), false);
});

test("sends only the explicit current question to the fixed OpenAI endpoint", async () => {
    const calls = mockExternalServices();
    const response = await worker.fetch(makeRequest(), makeEnvironment());
    assert.equal(response.status, 200);
    const result = await response.json();
    assert.equal(result.answer, "Eine allgemeine Antwort.");
    assert.match(result.requestId, /^[0-9a-f-]{36}$/i);

    const verificationCall = calls.find(({ url }) => url.includes("challenges.cloudflare.com"));
    assert.equal(new URLSearchParams(verificationCall.options.body).get("remoteip"), "203.0.113.10");
    const providerCall = calls.find(({ url }) => url === "https://api.openai.com/v1/responses");
    assert.ok(providerCall);
    assert.equal(providerCall.options.headers.Authorization, ["Bearer", "mocked-only"].join(" "));
    const sent = JSON.parse(providerCall.options.body);
    assert.equal(sent.input, "Wie plane ich meinen Tag?");
    assert.match(sent.instructions, /Antworte auf Deutsch\.$/);
    assert.equal(sent.store, false);
    assert.equal(sent.max_output_tokens, 300);
    assert.equal(sent.model, "gpt-4.1-mini");
    assert.equal("tools" in sent, false);
    assert.equal("conversation" in sent, false);
    assert.equal("previous_response_id" in sent, false);
});

test("keeps the existing versioned assistant endpoint as a compatible alias", async () => {
    mockExternalServices();
    const response = await worker.fetch(makeRequest({ path: "/v1/assistant/responses" }), makeEnvironment());
    assert.equal(response.status, 200);
});

test("accepts Cloudflare's signed access assertion header for an Access-protected route", async () => {
    mockExternalServices();
    const response = await worker.fetch(makeRequest({ accessHeader: true }), makeEnvironment());
    assert.equal(response.status, 200);
});

test("selects an isolated application profile and model only from the signed audience", async () => {
    const calls = mockExternalServices({
        turnstileHostname: "defense.example",
        turnstileAction: "defense_assistant"
    });
    const response = await worker.fetch(makeRequest({
        origin: DEFENSE_ORIGIN,
        accessToken: await createAccessToken({ audience: DEFENSE_AUDIENCE }),
        body: JSON.stringify({
            question: "Wie kann ich mich zivil vorbereiten?",
            locale: "de",
            consent: true,
            turnstileToken: "valid-turnstile-token"
        })
    }), makeEnvironment());
    assert.equal(response.status, 200);
    const providerCall = calls.find(({ url }) => url === "https://api.openai.com/v1/responses");
    const sent = JSON.parse(providerCall.options.body);
    assert.equal(sent.model, "gpt-4.1-mini");
    assert.match(sent.instructions, /getrennt autorisierten NIKNOLIG-Defense-Profil/);
    assert.doesNotMatch(sent.instructions, /Elektro-Engel/);
    assert.deepEqual(sent.input, "Wie kann ich mich zivil vorbereiten?");
    assert.equal("clientId" in sent, false);
    assert.equal("tools" in sent, false);
    assert.equal("conversation" in sent, false);
});

test("allows a configured native client without browser Origin or Turnstile", async () => {
    const calls = mockExternalServices();
    const response = await worker.fetch(makeRequest({
        origin: null,
        accessToken: await createAccessToken({ audience: NATIVE_AUDIENCE })
    }), makeEnvironment({ TURNSTILE_SECRET_KEY: "" }));
    assert.equal(response.status, 200);
    assert.equal(calls.some(({ url }) => url.includes("challenges.cloudflare.com")), false);
    const providerCall = calls.find(({ url }) => url === "https://api.openai.com/v1/responses");
    const sent = JSON.parse(providerCall.options.body);
    assert.equal(sent.model, "gpt-4.1-nano");
    assert.equal(sent.max_output_tokens, 120);
});

test("fails closed on malformed or unauthorized server-side client mappings", async () => {
    const calls = mockExternalServices();
    const invalidMappings = [
        "{",
        JSON.stringify([{ ...CLIENTS[0], model: "arbitrary-model" }]),
        JSON.stringify([{ ...CLIENTS[0], origins: ["http://insecure.example"] }]),
        JSON.stringify([CLIENTS[0], { ...CLIENTS[1], audience: ACCESS_AUDIENCE }])
    ];
    for (const AI_CLIENTS_JSON of invalidMappings) {
        const response = await worker.fetch(
            makeRequest(),
            makeEnvironment({ AI_CLIENTS_JSON })
        );
        assert.equal(response.status, 503);
    }
    assert.equal(calls.length, 0);
});

test("rejects missing consent, unknown history fields, and obvious sensitive inputs before provider access", async () => {
    const calls = mockExternalServices();
    const environment = makeEnvironment();
    const requests = [
        makeRequest({ consent: false }),
        makeRequest({
            body: JSON.stringify({
                question: "Hello",
                locale: "en",
                consent: true,
                turnstileToken: "valid-turnstile-token",
                history: ["previous chat"]
            })
        }),
        makeRequest({
            body: JSON.stringify({
                question: "Hello",
                locale: "en",
                consent: true,
                turnstileToken: "valid-turnstile-token",
                profile: "defense",
                model: "gpt-4.1-mini"
            })
        }),
        makeRequest({ question: "pass" + "word=secret" }),
        makeRequest({ question: "Meine Kartennummer ist 4111 1111 1111 1111" }),
        makeRequest({ question: "" }),
        makeRequest({ locale: "fr" }),
        makeRequest({ question: "Bitte\nleise" }),
        makeRequest({ question: "x".repeat(1_001) })
    ];
    for (const request of requests) {
        const response = await worker.fetch(request, environment);
        assert.equal(response.status, 400);
    }
    assert.equal(calls.length, 0);
});

test("rejects oversized and non-JSON requests", async () => {
    mockExternalServices();
    const environment = makeEnvironment();
    const oversized = await worker.fetch(makeRequest({ body: "x".repeat(8_193) }), environment);
    assert.equal(oversized.status, 413);
    const wrongType = await worker.fetch(makeRequest({ contentType: "text/plain" }), environment);
    assert.equal(wrongType.status, 415);
});

test("requires successful Turnstile verification for the expected host and action", async () => {
    const calls = mockExternalServices({ turnstile: false });
    const response = await worker.fetch(makeRequest(), makeEnvironment());
    assert.equal(response.status, 403);
    assert.equal(calls.some(({ url }) => url === "https://api.openai.com/v1/responses"), false);
});

test("uses generic provider failure responses and never returns provider diagnostics", async () => {
    mockExternalServices({ provider: false });
    const response = await worker.fetch(makeRequest(), makeEnvironment());
    assert.equal(response.status, 503);
    assert.deepEqual(await response.json(), { error: "service_unavailable" });
});

test("handles provider timeouts and malformed responses without exposing details", async () => {
    for (const provider of ["timeout", "malformed"]) {
        mockExternalServices({ provider });
        const response = await worker.fetch(makeRequest(), makeEnvironment());
        assert.equal(response.status, 503);
        assert.deepEqual(await response.json(), { error: "service_unavailable" });
        cleanups.pop()();
    }
});

test("rejects a foreign website origin before any external request", async () => {
    const calls = mockExternalServices();
    const response = await worker.fetch(makeRequest({ origin: "https://attacker.example" }), makeEnvironment());
    assert.equal(response.status, 403);
    assert.equal(calls.length, 0);
});

test("enforces a shared daily request ceiling and per-IP daily windows", async () => {
    const storage = new MemoryStorage();
    const guard = new AssistantGuard({ storage });
    const reserve = (ipHash, limits = {}) => guard.fetch(new Request("https://guard.internal/reserve", {
        method: "POST",
        body: JSON.stringify({
            ipHash,
            clientId: "test-client",
            reservationId: crypto.randomUUID(),
            dailyLimit: 2,
            appDailyLimit: 2,
            perIpDailyLimit: 2,
            perIpMinuteLimit: 2,
            concurrencyLimit: 2,
            ...limits
        })
    }));
    const ipA = "a".repeat(64);
    const ipB = "b".repeat(64);

    assert.deepEqual(await (await reserve(ipA)).json(), { allowed: true });
    assert.deepEqual(await (await reserve(ipB)).json(), { allowed: true });
    await assertRateLimited(await reserve("c".repeat(64)));
});

test("keeps per-application daily quotas isolated while retaining the shared platform ceiling", async () => {
    const storage = new MemoryStorage();
    const reserve = (clientId) => new AssistantGuard({ storage }).fetch(
        new Request("https://guard.internal/reserve", {
            method: "POST",
            body: JSON.stringify({
                ipHash: "a".repeat(64),
                clientId,
                reservationId: crypto.randomUUID(),
                dailyLimit: 10,
                appDailyLimit: 1,
                perIpDailyLimit: 10,
                perIpMinuteLimit: 10,
                concurrencyLimit: 10
            })
        })
    );
    assert.deepEqual(await (await reserve("niknolig-web")).json(), { allowed: true });
    await assertRateLimited(await reserve("niknolig-web"));
    assert.deepEqual(await (await reserve("niknolig-defense")).json(), { allowed: true });
});

test("limits per-IP request rates and simultaneous upstream work", async () => {
    const storage = new MemoryStorage();
    const reserve = async (ipHash, overrides = {}) => {
        const reservationId = crypto.randomUUID();
        const response = await new AssistantGuard({ storage }).fetch(new Request("https://guard.internal/reserve", {
            method: "POST",
            body: JSON.stringify({
                ipHash,
                clientId: "test-client",
                reservationId,
                dailyLimit: 10,
                appDailyLimit: 10,
                perIpDailyLimit: 10,
                perIpMinuteLimit: 10,
                concurrencyLimit: 1,
                ...overrides
            })
        }));
        return { response, reservationId };
    };
    const release = (reservationId) => new AssistantGuard({ storage }).fetch(new Request("https://guard.internal/release", {
        method: "POST",
        body: JSON.stringify({ reservationId })
    }));
    const ipA = "a".repeat(64);
    const ipB = "b".repeat(64);

    const first = await reserve(ipA, { perIpMinuteLimit: 1 });
    assert.deepEqual(await first.response.json(), { allowed: true });
    await release(first.reservationId);
    await assertRateLimited((await reserve(ipA, { perIpMinuteLimit: 1 })).response);

    const active = await reserve(ipB);
    assert.deepEqual(await active.response.json(), { allowed: true });
    await assertRateLimited((await reserve("c".repeat(64))).response);
    await release(active.reservationId);
    assert.deepEqual(await (await reserve("c".repeat(64))).response.json(), { allowed: true });
});

test("clears pseudonymous per-IP quota keys during its cleanup alarm", async () => {
    const storage = new MemoryStorage();
    const guard = new AssistantGuard({ storage });
    const response = await guard.fetch(new Request("https://guard.internal/reserve", {
        method: "POST",
        body: JSON.stringify({
            ipHash: "a".repeat(64),
            clientId: "test-client",
            reservationId: crypto.randomUUID(),
            dailyLimit: 5,
            appDailyLimit: 5,
            perIpDailyLimit: 5,
            perIpMinuteLimit: 5,
            concurrencyLimit: 2
        })
    }));
    assert.equal((await response.json()).allowed, true);
    assert.equal((await storage.list({ prefix: "ip:" })).size, 1);
    await guard.alarm();
    assert.equal((await storage.list({ prefix: "ip:" })).size, 0);
});

test("preflight permits only POST with Content-Type from the exact website origin", async () => {
    const response = await worker.fetch(new Request("https://gateway.example/v1/ai/responses", {
        method: "OPTIONS",
        headers: { Origin: ORIGIN }
    }), makeEnvironment());
    assert.equal(response.status, 204);
    assert.equal(response.headers.get("access-control-allow-origin"), ORIGIN);
    assert.equal(response.headers.get("access-control-allow-methods"), "POST, OPTIONS");
    assert.equal(
        response.headers.get("access-control-allow-headers"),
        "Authorization, Content-Type, Cf-Access-Jwt-Assertion"
    );
});
