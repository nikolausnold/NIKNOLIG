const ASSISTANT_PATHS = new Set(["/v1/ai/responses", "/v1/assistant/responses"]);
const HEALTH_PATH = "/v1/health";
const MAX_BODY_BYTES = 8_192;
const MAX_QUESTION_CODE_POINTS = 1_000;
const MAX_PROVIDER_RESPONSE_BYTES = 65_536;
const MAX_OUTPUT_TOKENS = 300;
const MAX_DAILY_REQUESTS = 100;
const MAX_DAILY_REQUESTS_PER_CLIENT = 50;
const MAX_REQUESTS_PER_IP_PER_DAY = 12;
const MAX_REQUESTS_PER_IP_PER_MINUTE = 3;
const MAX_CONCURRENT_REQUESTS = 2;
const PROVIDER_TIMEOUT_MS = 20_000;
const TURNSTILE_TIMEOUT_MS = 5_000;
const SUPPORTED_MODELS = new Set(["gpt-4.1-mini", "gpt-4.1-nano"]);
const ACCESS_TOKEN_TIMEOUT_MS = 5_000;
const ACCESS_JWKS_CACHE_MS = 300_000;

let cachedAccessJwks;

const PROFILE_INSTRUCTIONS = {
    niknolig: [
        "Du bist die allgemeine, menschenorientierte Informationsassistenz von NIKNOLIG.",
        "Antworte knapp, vorsichtig und in der angefragten Sprache.",
        "Gib keine Anleitung für gefährliche oder unautorisierte Handlungen.",
        "Bei akuter Gefahr: Abstand halten und den örtlichen Notruf kontaktieren.",
        "Nutze ausschließlich die aktuelle Frage; behaupte keinen Zugriff auf Daten anderer Anwendungen."
    ].join(" "),
    "elektro-engel": [
        "Du bist Elektro-Engel, eine zivile, allgemeine Assistenz von NIKNOLIG.",
        "Antworte knapp, vorsichtig und in der angefragten Sprache.",
        "Gib keine Anleitung für Elektroarbeiten, Reparaturen, das Umgehen von Schutzmaßnahmen oder andere gefährliche Handlungen.",
        "Bei akuter Gefahr: Abstand halten und den örtlichen Notruf kontaktieren.",
        "Nutze ausschließlich die aktuelle Frage; behaupte keinen Zugriff auf Daten anderer Anwendungen."
    ].join(" "),
    defense: [
        "Du bist eine zivile, informationsorientierte Assistenz im getrennt autorisierten NIKNOLIG-Defense-Profil.",
        "Beschränke dich auf allgemeine zivile Vorsorge, Resilienz und sichere Orientierung.",
        "Gib keine Anleitungen zu Gewalt, Waffen, Zielauswahl, Überwachung, taktischen Operationen oder Umgehung von Schutzmaßnahmen.",
        "Behaupte keinen Zugriff auf Defense-Daten, andere Anwendungen oder aktuelle Lageinformationen.",
        "Nutze ausschließlich die aktuelle Frage. Bei akuter Gefahr: Abstand halten und den örtlichen Notruf kontaktieren."
    ].join(" ")
};

const SECURITY_HEADERS = {
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer"
};

function jsonResponse(status, body, origin, allowedOrigins = [], additionalHeaders = {}) {
    const headers = new Headers({
        ...SECURITY_HEADERS,
        "Content-Type": "application/json; charset=utf-8",
        ...additionalHeaders
    });
    if (origin && allowedOrigins.includes(origin)) {
        headers.set("Access-Control-Allow-Origin", origin);
        headers.set("Vary", "Origin");
    }
    return new Response(JSON.stringify(body), { status, headers });
}

function healthResponse(origin, allowedOrigins) {
    return jsonResponse(200, {
        status: "ok",
        service: "niknolig-api",
        apiVersion: 1
    }, origin, allowedOrigins);
}

function getClientConfigurations(env) {
    if (typeof env.AI_CLIENTS_JSON !== "string" || env.AI_CLIENTS_JSON.length > 16_384) return null;
    let clients;
    try {
        clients = JSON.parse(env.AI_CLIENTS_JSON);
    } catch {
        return null;
    }
    if (!Array.isArray(clients) || clients.length < 1 || clients.length > 32) return null;

    const audiences = new Set();
    const clientIds = new Set();
    for (const client of clients) {
        if (!client || typeof client !== "object" || Array.isArray(client)) return null;
        const keys = Object.keys(client).sort();
        if (keys.some((key) => ![
            "audience", "clientId", "profile", "model", "origins", "turnstileAction", "maxOutputTokens"
        ].includes(key))) return null;
        if (
            typeof client.audience !== "string" ||
            client.audience.length < 1 ||
            client.audience.length > 256 ||
            client.audience.trim() !== client.audience ||
            /[\u0000-\u0020\u007F]/.test(client.audience) ||
            typeof client.clientId !== "string" ||
            !/^[a-z0-9][a-z0-9-]{1,62}$/.test(client.clientId) ||
            !Object.hasOwn(PROFILE_INSTRUCTIONS, client.profile) ||
            !SUPPORTED_MODELS.has(client.model) ||
            !Array.isArray(client.origins) ||
            client.origins.length > 8 ||
            (client.maxOutputTokens !== undefined &&
                (!Number.isInteger(client.maxOutputTokens) ||
                    client.maxOutputTokens < 1 ||
                    client.maxOutputTokens > MAX_OUTPUT_TOKENS))
        ) return null;
        if (audiences.has(client.audience) || clientIds.has(client.clientId)) return null;
        audiences.add(client.audience);
        clientIds.add(client.clientId);

        const origins = new Set();
        for (const origin of client.origins) {
            if (typeof origin !== "string" || origin.length > 2_048) return null;
            try {
                const parsedOrigin = new URL(origin);
                if (
                    parsedOrigin.protocol !== "https:" ||
                    parsedOrigin.origin !== origin ||
                    parsedOrigin.username ||
                    parsedOrigin.password
                ) return null;
            } catch {
                return null;
            }
            if (origins.has(origin)) return null;
            origins.add(origin);
        }
        if (client.origins.length > 0 &&
            (typeof client.turnstileAction !== "string" ||
                !/^[A-Za-z0-9_-]{1,32}$/.test(client.turnstileAction))) return null;
        if (client.origins.length === 0 && client.turnstileAction !== undefined) return null;
    }
    return clients;
}

function getAllowedOrigins(clients) {
    return [...new Set(clients.flatMap((client) => client.origins))];
}

function isCardNumber(value) {
    const digits = value.replace(/\D/g, "");
    if (digits.length < 13 || digits.length > 19) return false;
    let sum = 0;
    let doubleDigit = false;
    for (let index = digits.length - 1; index >= 0; index -= 1) {
        let digit = Number(digits[index]);
        if (doubleDigit) {
            digit *= 2;
            if (digit > 9) digit -= 9;
        }
        sum += digit;
        doubleDigit = !doubleDigit;
    }
    return sum % 10 === 0;
}

function containsObviousSecrets(question) {
    if (/-----BEGIN [A-Z ]*PRIVATE KEY-----/i.test(question)) return true;
    if (/\b(?:sk-[A-Za-z0-9_-]{16,}|AKIA[0-9A-Z]{16})\b/.test(question)) return true;
    if (/\b(?:password|passwort|api[\s_-]?key|secret|token)\s*[:=]/i.test(question)) return true;
    return question.match(/\b(?:\d[ -]?){13,19}\b/g)?.some(isCardNumber) ?? false;
}

async function readBoundedBody(request) {
    const declaredLength = request.headers.get("content-length");
    if (declaredLength !== null && (!/^\d+$/.test(declaredLength) || Number(declaredLength) > MAX_BODY_BYTES)) {
        throw new RangeError("body_too_large");
    }
    if (!request.body) throw new TypeError("invalid_body");

    const reader = request.body.getReader();
    const chunks = [];
    let totalBytes = 0;
    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            totalBytes += value.byteLength;
            if (totalBytes > MAX_BODY_BYTES) {
                await reader.cancel();
                throw new RangeError("body_too_large");
            }
            chunks.push(value);
        }
    } finally {
        reader.releaseLock();
    }

    const body = new Uint8Array(totalBytes);
    let offset = 0;
    for (const chunk of chunks) {
        body.set(chunk, offset);
        offset += chunk.byteLength;
    }
    return new TextDecoder("utf-8", { fatal: true }).decode(body);
}

function validatePayload(payload, requiresTurnstile) {
    if (!payload || typeof payload !== "object" || Array.isArray(payload)) return false;
    const keys = Object.keys(payload).sort();
    const expectedKeys = requiresTurnstile
        ? "consent,locale,question,turnstileToken"
        : "consent,locale,question";
    if (keys.join(",") !== expectedKeys) return false;
    if (payload.consent !== true) return false;
    if (payload.locale !== "de" && payload.locale !== "en") return false;
    if (typeof payload.question !== "string") return false;
    if (requiresTurnstile &&
        (typeof payload.turnstileToken !== "string" ||
            payload.turnstileToken.length < 1 ||
            payload.turnstileToken.length > 2_048)) return false;

    const question = payload.question.trim();
    if (
        question.length === 0 ||
        [...question].length > MAX_QUESTION_CODE_POINTS ||
        /[\u0000-\u001F\u007F]/u.test(question) ||
        containsObviousSecrets(question)
    ) return false;
    return true;
}

async function verifyTurnstile(token, remoteIp, origin, action, env) {
    const response = await fetch("https://challenges.cloudflare.com/turnstile/v0/siteverify", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
            secret: env.TURNSTILE_SECRET_KEY,
            response: token,
            remoteip: remoteIp
        }),
        redirect: "error",
        signal: AbortSignal.timeout(TURNSTILE_TIMEOUT_MS)
    });
    if (!response.ok) return false;
    const result = await response.json();
    return result.success === true &&
        result.hostname === new URL(origin).hostname &&
        result.action === action;
}

function decodeBase64Url(value) {
    if (!/^[A-Za-z0-9_-]+$/.test(value)) throw new TypeError("invalid_token");
    const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
    const decoded = atob(base64.padEnd(Math.ceil(base64.length / 4) * 4, "="));
    return Uint8Array.from(decoded, (character) => character.charCodeAt(0));
}

function parseJsonSegment(segment) {
    return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(decodeBase64Url(segment)));
}

function getAccessConfiguration(env) {
    const teamDomain = env.CF_ACCESS_TEAM_DOMAIN;
    if (
        typeof teamDomain !== "string" ||
        !/^[a-z0-9.-]{1,253}$/i.test(teamDomain) ||
        teamDomain !== teamDomain.toLowerCase() ||
        teamDomain.startsWith(".") ||
        teamDomain.endsWith(".") ||
        teamDomain.includes("..")
    ) return null;

    const issuer = `https://${teamDomain}`;
    const url = new URL(`${issuer}/cdn-cgi/access/certs`);
    if (url.hostname !== teamDomain || url.protocol !== "https:") return null;
    return { issuer, jwksUrl: url.href };
}

async function loadAccessKeys(jwksUrl) {
    const now = Date.now();
    if (cachedAccessJwks?.url === jwksUrl && cachedAccessJwks.expiresAt > now) {
        return cachedAccessJwks.keys;
    }

    const response = await fetch(jwksUrl, {
        redirect: "error",
        signal: AbortSignal.timeout(ACCESS_TOKEN_TIMEOUT_MS)
    });
    if (!response.ok) throw new Error("access_keys_unavailable");
    const payload = await readProviderResponse(response, 32_768);
    if (!Array.isArray(payload.keys) || payload.keys.length < 1 || payload.keys.length > 16) {
        throw new Error("invalid_access_keys");
    }
    cachedAccessJwks = {
        url: jwksUrl,
        expiresAt: now + ACCESS_JWKS_CACHE_MS,
        keys: payload.keys
    };
    return payload.keys;
}

async function verifyAccessToken(request, env, clients) {
    const configuration = getAccessConfiguration(env);
    if (!configuration) return null;

    const authorization = request.headers.get("Authorization") || "";
    const accessAssertion = request.headers.get("Cf-Access-Jwt-Assertion") || "";
    let token = accessAssertion;
    if (authorization) {
        if (!authorization.toLowerCase().startsWith("bearer ")) return null;
        const bearerToken = authorization.slice(7).trim();
        if (accessAssertion && accessAssertion !== bearerToken) return null;
        token = bearerToken;
    }
    if (token.length < 1 || token.length > 8_192) return null;
    const segments = token.split(".");
    if (segments.length !== 3) return null;

    try {
        const header = parseJsonSegment(segments[0]);
        const claims = parseJsonSegment(segments[1]);
        if (
            header.alg !== "RS256" ||
            typeof header.kid !== "string" ||
            header.kid.length < 1 ||
            header.kid.length > 256 ||
            claims.iss !== configuration.issuer ||
            !(typeof claims.aud === "string" ||
                (Array.isArray(claims.aud) && claims.aud.length === 1)) ||
            typeof claims.sub !== "string" ||
            claims.sub.length < 1 ||
            !Number.isInteger(claims.exp) ||
            claims.exp <= Math.floor(Date.now() / 1_000) ||
            (claims.nbf !== undefined &&
                (!Number.isInteger(claims.nbf) || claims.nbf > Math.floor(Date.now() / 1_000) + 30)) ||
            (claims.iat !== undefined &&
                (!Number.isInteger(claims.iat) || claims.iat > Math.floor(Date.now() / 1_000) + 30))
        ) return null;

        const audience = Array.isArray(claims.aud) ? claims.aud[0] : claims.aud;
        const client = clients.find((candidate) => candidate.audience === audience);
        if (!client) return null;

        const jwks = await loadAccessKeys(configuration.jwksUrl);
        const jwk = jwks.find((key) =>
            key.kid === header.kid &&
            key.kty === "RSA" &&
            (!key.alg || key.alg === "RS256") &&
            (!key.use || key.use === "sig")
        );
        if (!jwk) return null;

        const publicKey = await crypto.subtle.importKey(
            "jwk",
            jwk,
            { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
            false,
            ["verify"]
        );
        const signedContent = new TextEncoder().encode(`${segments[0]}.${segments[1]}`);
        const valid = await crypto.subtle.verify(
            "RSASSA-PKCS1-v1_5",
            publicKey,
            decodeBase64Url(segments[2]),
            signedContent
        );
        return valid ? client : null;
    } catch {
        return null;
    }
}

async function readProviderResponse(response, maxBytes = MAX_PROVIDER_RESPONSE_BYTES) {
    if (!response.body) return null;
    const reader = response.body.getReader();
    const chunks = [];
    let totalBytes = 0;
    try {
        while (true) {
            const { done, value } = await reader.read();
            if (done) break;
            totalBytes += value.byteLength;
            if (totalBytes > maxBytes) {
                await reader.cancel();
                return null;
            }
            chunks.push(value);
        }
    } finally {
        reader.releaseLock();
    }
    const bytes = new Uint8Array(totalBytes);
    let offset = 0;
    for (const chunk of chunks) {
        bytes.set(chunk, offset);
        offset += chunk.byteLength;
    }
    try {
        return JSON.parse(new TextDecoder("utf-8", { fatal: true }).decode(bytes));
    } catch {
        return null;
    }
}

function extractAnswer(result) {
    if (typeof result?.output_text === "string") return result.output_text;
    if (!Array.isArray(result?.output)) return null;
    const text = [];
    for (const item of result.output) {
        if (item?.type !== "message" || !Array.isArray(item.content)) continue;
        for (const part of item.content) {
            if (part?.type === "output_text" && typeof part.text === "string") text.push(part.text);
            if (part?.type === "refusal" && typeof part.refusal === "string") text.push(part.refusal);
        }
    }
    return text.length ? text.join("\n") : null;
}

async function hashIp(ip, secret) {
    const encoder = new TextEncoder();
    const key = await crypto.subtle.importKey(
        "raw",
        encoder.encode(secret),
        { name: "HMAC", hash: "SHA-256" },
        false,
        ["sign"]
    );
    const digest = await crypto.subtle.sign("HMAC", key, encoder.encode(ip));
    return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function reserveRequest(env, clientId, ipHash) {
    const id = env.ASSISTANT_GUARD.idFromName("global");
    const guard = env.ASSISTANT_GUARD.get(id);
    const reservationId = crypto.randomUUID();
    const response = await guard.fetch("https://guard.internal/reserve", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            ipHash,
            clientId,
            reservationId,
            dailyLimit: MAX_DAILY_REQUESTS,
            appDailyLimit: MAX_DAILY_REQUESTS_PER_CLIENT,
            perIpDailyLimit: MAX_REQUESTS_PER_IP_PER_DAY,
            perIpMinuteLimit: MAX_REQUESTS_PER_IP_PER_MINUTE,
            concurrencyLimit: MAX_CONCURRENT_REQUESTS
        })
    });
    return { ...await response.json(), reservationId };
}

async function releaseRequest(env, reservationId) {
    const id = env.ASSISTANT_GUARD.idFromName("global");
    const guard = env.ASSISTANT_GUARD.get(id);
    await guard.fetch("https://guard.internal/release", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ reservationId })
    });
}

async function handleAssistant(request, env, origin, clients, allowedOrigins) {
    if (
        typeof env.OPENAI_API_KEY !== "string" ||
        env.OPENAI_API_KEY.length === 0 ||
        typeof env.RATE_LIMIT_HASH_SECRET !== "string" ||
        new TextEncoder().encode(env.RATE_LIMIT_HASH_SECRET).byteLength < 32 ||
        !env.ASSISTANT_GUARD ||
        !getAccessConfiguration(env) ||
        (origin !== null &&
            (typeof env.TURNSTILE_SECRET_KEY !== "string" || env.TURNSTILE_SECRET_KEY.length === 0))
    ) {
        return jsonResponse(503, { error: "service_unavailable" }, origin, allowedOrigins);
    }
    if (request.method !== "POST") {
        return jsonResponse(405, { error: "method_not_allowed" }, origin, allowedOrigins, { Allow: "POST, OPTIONS" });
    }
    const client = await verifyAccessToken(request, env, clients);
    if (!client) {
        return jsonResponse(401, { error: "unauthorized" }, origin, allowedOrigins, { "WWW-Authenticate": "Bearer" });
    }
    if (
        (client.origins.length > 0 && (!origin || !client.origins.includes(origin))) ||
        (client.origins.length === 0 && origin !== null)
    ) {
        return jsonResponse(403, { error: "forbidden" }, null);
    }
    const clientOrigins = client.origins;
    const requiresTurnstile = origin !== null;
    const contentType = request.headers.get("content-type")?.toLowerCase() || "";
    if (!/^application\/json(?:\s*;|$)/.test(contentType)) {
        return jsonResponse(415, { error: "unsupported_media_type" }, origin, clientOrigins);
    }

    let payload;
    try {
        payload = JSON.parse(await readBoundedBody(request));
    } catch (error) {
        return jsonResponse(error instanceof RangeError ? 413 : 400, {
            error: error instanceof RangeError ? "request_too_large" : "invalid_request"
        }, origin, clientOrigins);
    }
    if (!validatePayload(payload, requiresTurnstile)) {
        return jsonResponse(400, { error: "invalid_request" }, origin, clientOrigins);
    }
    const remoteIp = request.headers.get("CF-Connecting-IP");
    if (!remoteIp || remoteIp.length > 45) {
        return jsonResponse(503, { error: "service_unavailable" }, origin, clientOrigins);
    }
    let ipHash;
    let reservation;
    try {
        ipHash = await hashIp(remoteIp, env.RATE_LIMIT_HASH_SECRET);
        reservation = await reserveRequest(env, client.clientId, ipHash);
    } catch {
        return jsonResponse(503, { error: "service_unavailable" }, origin, clientOrigins);
    }
    if (!reservation.allowed) {
        return jsonResponse(429, { error: "rate_limited" }, origin, clientOrigins, {
            "Retry-After": String(reservation.retryAfter || 60)
        });
    }

    try {
        if (requiresTurnstile &&
            !await verifyTurnstile(payload.turnstileToken, remoteIp, origin, client.turnstileAction, env)) {
            return jsonResponse(403, { error: "request_not_verified" }, origin, clientOrigins);
        }
        const upstream = await fetch("https://api.openai.com/v1/responses", {
            method: "POST",
            headers: {
                Authorization: "Bearer " + env.OPENAI_API_KEY,
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                model: client.model,
                instructions: `${PROFILE_INSTRUCTIONS[client.profile]} Antworte auf ${payload.locale === "de" ? "Deutsch" : "Englisch"}.`,
                input: payload.question.trim(),
                max_output_tokens: client.maxOutputTokens ?? MAX_OUTPUT_TOKENS,
                store: false
            }),
            redirect: "error",
            signal: AbortSignal.timeout(PROVIDER_TIMEOUT_MS)
        });
        if (!upstream.ok) {
            await upstream.body?.cancel();
            return jsonResponse(503, { error: "service_unavailable" }, origin, clientOrigins);
        }

        const result = await readProviderResponse(upstream);
        const answer = extractAnswer(result);
        if (typeof answer !== "string" || answer.trim().length === 0 || [...answer].length > 4_000) {
            return jsonResponse(503, { error: "service_unavailable" }, origin, clientOrigins);
        }
        return jsonResponse(200, {
            answer: answer.trim(),
            requestId: crypto.randomUUID()
        }, origin, clientOrigins);
    } catch {
        return jsonResponse(503, { error: "service_unavailable" }, origin, clientOrigins);
    } finally {
        try {
            await releaseRequest(env, reservation.reservationId);
        } catch {
            // Expired reservations are removed by the next durable quota transaction.
        }
    }
}

export class AssistantGuard {
    constructor(ctx) {
        this.ctx = ctx;
    }

    async fetch(request) {
        if (request.method !== "POST") return new Response(null, { status: 405 });
        const path = new URL(request.url).pathname;
        if (path === "/release") {
            let payload;
            try {
                payload = await request.json();
            } catch {
                return new Response(null, { status: 400 });
            }
            if (typeof payload.reservationId !== "string" || !/^[0-9a-f-]{36}$/i.test(payload.reservationId)) {
                return new Response(null, { status: 400 });
            }
            await this.ctx.storage.delete(`active:${payload.reservationId}`);
            return Response.json({ released: true });
        }
        if (path !== "/reserve") return new Response(null, { status: 404 });

        let payload;
        try {
            payload = await request.json();
        } catch {
            return Response.json({ allowed: false, retryAfter: 60 });
        }
        if (
            typeof payload.ipHash !== "string" ||
            !/^[a-f0-9]{64}$/.test(payload.ipHash) ||
            typeof payload.clientId !== "string" ||
            !/^[a-z0-9][a-z0-9-]{1,62}$/.test(payload.clientId) ||
            typeof payload.reservationId !== "string" ||
            !/^[0-9a-f-]{36}$/i.test(payload.reservationId) ||
            !Number.isInteger(payload.dailyLimit) ||
            !Number.isInteger(payload.appDailyLimit) ||
            !Number.isInteger(payload.perIpDailyLimit) ||
            !Number.isInteger(payload.perIpMinuteLimit) ||
            !Number.isInteger(payload.concurrencyLimit)
        ) return Response.json({ allowed: false, retryAfter: 60 });

        const now = Date.now();
        const day = new Date(now).toISOString().slice(0, 10);
        const minute = Math.floor(now / 60_000);
        const appKey = `app:${payload.clientId}`;
        const ipKey = `ip:${payload.clientId}:${payload.ipHash}`;
        const alarm = await this.ctx.storage.getAlarm();
        if (alarm === null) await this.ctx.storage.setAlarm(now + 86_400_000);
        let allowed;
        allowed = await this.ctx.storage.transaction(async (transaction) => {
                const globalEntry = await transaction.get("global") || { day, count: 0 };
                const appEntry = await transaction.get(appKey) || { day, count: 0 };
                const ipEntry = await transaction.get(ipKey) || { day, dailyCount: 0, minute, minuteCount: 0 };
                if (globalEntry.day !== day) Object.assign(globalEntry, { day, count: 0 });
                if (appEntry.day !== day) Object.assign(appEntry, { day, count: 0 });
                if (ipEntry.day !== day) Object.assign(ipEntry, { day, dailyCount: 0 });
                if (ipEntry.minute !== minute) Object.assign(ipEntry, { minute, minuteCount: 0 });
                const activeEntries = await transaction.list({ prefix: "active:" });
                let activeCount = 0;
                for (const [key, expiresAt] of activeEntries) {
                    if (typeof expiresAt !== "number" || expiresAt <= now) {
                        await transaction.delete(key);
                    } else {
                        activeCount += 1;
                    }
                }

                if (
                    globalEntry.count >= payload.dailyLimit ||
                    appEntry.count >= payload.appDailyLimit ||
                    ipEntry.dailyCount >= payload.perIpDailyLimit
                ) {
                    const tomorrow = new Date(`${day}T00:00:00.000Z`).getTime() + 86_400_000;
                    return { allowed: false, retryAfter: Math.max(1, Math.ceil((tomorrow - now) / 1_000)) };
                }
                if (ipEntry.minuteCount >= payload.perIpMinuteLimit) {
                    return { allowed: false, retryAfter: Math.max(1, 60 - Math.floor((now % 60_000) / 1_000)) };
                }
                if (activeCount >= payload.concurrencyLimit) {
                    return { allowed: false, retryAfter: 10 };
                }

                globalEntry.count += 1;
                appEntry.count += 1;
                ipEntry.dailyCount += 1;
                ipEntry.minuteCount += 1;
                await transaction.put("global", globalEntry);
                await transaction.put(appKey, appEntry);
                await transaction.put(ipKey, ipEntry);
                await transaction.put(`active:${payload.reservationId}`, now + 30_000);
                return { allowed: true };
        });
        if (!allowed.allowed) return Response.json(allowed);

        return Response.json({ allowed: true });
    }

    async alarm() {
        const entries = await this.ctx.storage.list({ prefix: "ip:" });
        for (const key of entries.keys()) await this.ctx.storage.delete(key);
    }
}

export default {
    async fetch(request, env) {
        const url = new URL(request.url);
        const origin = request.headers.get("Origin");
        const clients = getClientConfigurations(env) || [];
        const allowedOrigins = getAllowedOrigins(clients);

        if (url.pathname === HEALTH_PATH && request.method === "GET") {
            return healthResponse(origin, allowedOrigins);
        }
        if (!ASSISTANT_PATHS.has(url.pathname)) {
            return jsonResponse(404, { error: "not_found" }, origin, allowedOrigins);
        }
        if (!getClientConfigurations(env)) {
            return jsonResponse(503, { error: "service_unavailable" }, null);
        }
        if (request.method === "OPTIONS") {
            if (!origin || !allowedOrigins.includes(origin)) {
                return jsonResponse(403, { error: "forbidden" }, null);
            }
            return new Response(null, {
                status: 204,
                headers: {
                    ...SECURITY_HEADERS,
                    "Access-Control-Allow-Origin": origin,
                    "Access-Control-Allow-Methods": "POST, OPTIONS",
                    "Access-Control-Allow-Headers": "Authorization, Content-Type, Cf-Access-Jwt-Assertion",
                    "Access-Control-Max-Age": "600",
                    Vary: "Origin"
                }
            });
        }
        if (origin && !allowedOrigins.includes(origin)) {
            return jsonResponse(403, { error: "forbidden" }, null);
        }
        if (request.method !== "POST") {
            return jsonResponse(405, { error: "method_not_allowed" }, origin, allowedOrigins, {
                Allow: "POST, OPTIONS"
            });
        }
        return handleAssistant(request, env, origin, clients, allowedOrigins);
    }
};
