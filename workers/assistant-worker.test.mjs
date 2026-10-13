import assert from "node:assert/strict";
import { afterEach, test } from "node:test";
import worker, { AssistantGuard } from "./assistant-worker.mjs";

const ORIGIN = "https://nikolausnold.github.io";
const cleanups = [];

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
        return Promise.resolve(new Map(
            [...this.values].filter(([key]) => key.startsWith(prefix))
        ));
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
        RATE_LIMIT_HASH_SECRET: "mocked-only",
        ASSISTANT_GUARD: new FakeGuardBinding(),
        ...overrides
    };
}

function mockExternalServices({ turnstile = true, provider = true } = {}) {
    const calls = [];
    const originalFetch = globalThis.fetch;
    globalThis.fetch = async (url, options = {}) => {
        calls.push({ url: String(url), options });
        if (String(url).includes("challenges.cloudflare.com")) {
            const body = new URLSearchParams(options.body);
            const valid = turnstile && body.get("response") === "valid-turnstile-token";
            return Response.json({
                success: valid,
                hostname: valid ? "nikolausnold.github.io" : "invalid.example",
                action: valid ? "niknolig_assistant" : "other"
            });
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
    body,
    contentType = "application/json"
} = {}) {
    const payload = body ?? JSON.stringify({ question, locale, consent, turnstileToken });
    return new Request("https://gateway.example/v1/assistant/responses", {
        method: "POST",
        headers: {
            Origin: origin,
            "CF-Connecting-IP": ip,
            "Content-Type": contentType
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

test("keeps the public health endpoint and limits CORS to the Pages origin", async () => {
    const health = await worker.fetch(new Request("https://gateway.example/v1/health", {
        headers: { Origin: ORIGIN }
    }), {});
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), {
        status: "ok",
        service: "niknolig-api",
        apiVersion: 1
    });
    assert.equal(health.headers.get("access-control-allow-origin"), ORIGIN);

    const denied = await worker.fetch(new Request("https://gateway.example/v1/assistant/responses", {
        method: "OPTIONS",
        headers: { Origin: "https://attacker.example" }
    }), {});
    assert.equal(denied.status, 403);
    assert.equal(denied.headers.get("access-control-allow-origin"), null);
});

test("fails closed when any server secret or quota binding is missing", async () => {
    const calls = mockExternalServices();
    const missingConfiguration = [
        { OPENAI_API_KEY: "" },
        { TURNSTILE_SECRET_KEY: "" },
        { RATE_LIMIT_HASH_SECRET: "" },
        { ASSISTANT_GUARD: undefined }
    ];
    for (const overrides of missingConfiguration) {
        const response = await worker.fetch(makeRequest(), makeEnvironment(overrides));
        assert.equal(response.status, 503);
    }
    assert.equal(calls.length, 0);
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
    assert.equal("tools" in sent, false);
    assert.equal("conversation" in sent, false);
    assert.equal("previous_response_id" in sent, false);
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

test("enforces a shared daily request ceiling, per-IP windows, and concurrency", async () => {
    const storage = new MemoryStorage();
    const guard = new AssistantGuard({ storage });
    const reserve = (ipHash, limits = {}) => guard.fetch(new Request("https://guard.internal/reserve", {
        method: "POST",
        body: JSON.stringify({
            ipHash,
            dailyLimit: 2,
            perIpDailyLimit: 2,
            perIpMinuteLimit: 2,
            concurrencyLimit: 2,
            ...limits
        })
    }));
    const release = () => guard.fetch(new Request("https://guard.internal/release", { method: "POST" }));
    const ipA = "a".repeat(64);
    const ipB = "b".repeat(64);

    assert.deepEqual(await (await reserve(ipA)).json(), { allowed: true });
    await release();
    assert.deepEqual(await (await reserve(ipB)).json(), { allowed: true });
    await release();
    await assertRateLimited(await reserve("c".repeat(64)));
});

test("limits per-IP request rates and simultaneous upstream work", async () => {
    const guard = new AssistantGuard({ storage: new MemoryStorage() });
    const reserve = (ipHash, overrides = {}) => guard.fetch(new Request("https://guard.internal/reserve", {
        method: "POST",
        body: JSON.stringify({
            ipHash,
            dailyLimit: 10,
            perIpDailyLimit: 10,
            perIpMinuteLimit: 10,
            concurrencyLimit: 1,
            ...overrides
        })
    }));
    const release = () => guard.fetch(new Request("https://guard.internal/release", { method: "POST" }));
    const ipA = "a".repeat(64);
    const ipB = "b".repeat(64);

    assert.deepEqual(await (await reserve(ipA, { perIpMinuteLimit: 1 })).json(), { allowed: true });
    await release();
    await assertRateLimited(await reserve(ipA, { perIpMinuteLimit: 1 }));
    assert.deepEqual(await (await reserve(ipB)).json(), { allowed: true });
    await assertRateLimited(await reserve("c".repeat(64)));
    await release();
    assert.deepEqual(await (await reserve("c".repeat(64))).json(), { allowed: true });
});

test("clears pseudonymous per-IP quota keys during its cleanup alarm", async () => {
    const storage = new MemoryStorage();
    const guard = new AssistantGuard({ storage });
    const response = await guard.fetch(new Request("https://guard.internal/reserve", {
        method: "POST",
        body: JSON.stringify({
            ipHash: "a".repeat(64),
            dailyLimit: 5,
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
    const response = await worker.fetch(new Request("https://gateway.example/v1/assistant/responses", {
        method: "OPTIONS",
        headers: { Origin: ORIGIN }
    }), {});
    assert.equal(response.status, 204);
    assert.equal(response.headers.get("access-control-allow-origin"), ORIGIN);
    assert.equal(response.headers.get("access-control-allow-methods"), "POST, OPTIONS");
    assert.equal(response.headers.get("access-control-allow-headers"), "Content-Type");
});
