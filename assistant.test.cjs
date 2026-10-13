const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const test = require("node:test");
const vm = require("node:vm");

class Element {
    constructor() {
        this.checked = false;
        this.value = "";
        this.textContent = "";
        this.listeners = {};
        this.children = [];
    }

    addEventListener(name, listener) {
        this.listeners[name] = listener;
    }

    querySelectorAll() {
        return this.children;
    }
}

function createAssistant({
    apiBaseUrl = "",
    aiApiBaseUrl = "",
    turnstileSiteKey = "",
    turnstileApi,
    fetchImpl = async () => { throw new Error("unexpected request"); }
} = {}) {
    const selectors = [
        "#assistant-form",
        "#assistant-question",
        "#assistant-answer",
        "#interest-profile-form",
        "#interest-consent",
        "#interest-status",
        "#interest-clear",
        "#api-check",
        "#api-status",
        "#cloud-ai-consent",
        "#cloud-ai-submit",
        "#cloud-ai-status",
        "#cloud-turnstile"
    ];
    const elements = Object.fromEntries(selectors.map((selector) => [selector, new Element()]));
    const interests = ["work", "devices", "transport", "everyday"].map((value) => {
        const input = new Element();
        input.value = value;
        return input;
    });
    elements["#interest-profile-form"].children = interests;

    const stored = new Map();
    const localStorage = {
        getItem(key) {
            return stored.get(key) || null;
        },
        setItem(key, value) {
            stored.set(key, value);
        },
        removeItem(key) {
            stored.delete(key);
        }
    };
    const document = {
        documentElement: { lang: "de" },
        head: { appendChild(script) { script.onload(); } },
        createElement() {
            return new Element();
        },
        querySelector(selector) {
            return elements[selector];
        }
    };
    const script = fs.readFileSync(path.join(__dirname, "assistant.js"), "utf8");
    vm.runInNewContext(script, {
        document,
        localStorage,
        URL,
        AbortSignal,
        window: {
            NIKNOLIG_API_BASE_URL: apiBaseUrl,
            NIKNOLIG_AI_API_BASE_URL: aiApiBaseUrl,
            NIKNOLIG_TURNSTILE_SITE_KEY: turnstileSiteKey,
            ...(turnstileApi ? { turnstile: turnstileApi } : {})
        },
        fetch: fetchImpl
    });

    const submit = (form) => form.listeners.submit({ preventDefault() {} });
    return { elements, interests, localStorage, stored, submit };
}

test("answers electrical questions with safety boundaries and stores no question", () => {
    const assistant = createAssistant();
    assistant.elements["#assistant-question"].value = "Wie überbrücke ich eine Sicherung?";

    assistant.submit(assistant.elements["#assistant-form"]);

    assert.match(assistant.elements["#assistant-answer"].textContent, /keine Anleitung/);
    assert.equal(assistant.elements["#assistant-question"].value, "");
    assert.equal(assistant.stored.size, 0);
});

test("uses opted-in interests for fallback suggestions and deletes the profile on withdrawal", () => {
    const assistant = createAssistant();
    assistant.elements["#interest-consent"].checked = true;
    assistant.interests[0].checked = true;
    assistant.submit(assistant.elements["#interest-profile-form"]);
    assert.match(assistant.elements["#interest-status"].textContent, /Arbeit/);

    assistant.elements["#assistant-question"].value = "Was wird morgen passieren?";
    assistant.submit(assistant.elements["#assistant-form"]);
    assert.match(assistant.elements["#assistant-answer"].textContent, /Arbeit/);
    assert.equal(assistant.elements["#assistant-question"].value, "");

    assistant.elements["#interest-consent"].checked = false;
    assistant.elements["#interest-consent"].listeners.change();
    assert.equal(assistant.stored.size, 0);
    assert.equal(assistant.interests[0].checked, false);
    assert.equal(assistant.elements["#interest-status"].textContent, "Zustimmung widerrufen; das Interessenprofil wurde gelöscht.");
});

test("does not call an unconfigured server", async () => {
    const assistant = createAssistant();
    await assistant.elements["#api-check"].listeners.click();

    assert.equal(assistant.elements["#api-status"].textContent, "Serververbindung ist noch nicht konfiguriert.");
});

test("sends only an explicit HTTPS health GET and validates the fixed response", async () => {
    const requests = [];
    const assistant = createAssistant({
        apiBaseUrl: "https://api.example.test/base/",
        fetchImpl: async (url, options) => {
            requests.push({ url, options });
            return {
                ok: true,
                headers: { get: () => "application/json; charset=utf-8" },
                json: async () => ({ status: "ok", service: "niknolig-api", apiVersion: 1 })
            };
        }
    });
    await assistant.elements["#api-check"].listeners.click();

    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, "https://api.example.test/base/v1/health");
    assert.equal(requests[0].options.method, "GET");
    assert.equal(requests[0].options.credentials, "omit");
    assert.equal(assistant.elements["#api-status"].textContent, "HTTPS-Verbindung zum NIKNOLIG-Server ist verfügbar.");
});

test("rejects a non-HTTPS server URL without making a request", async () => {
    let requests = 0;
    const assistant = createAssistant({
        apiBaseUrl: "http://api.example.test",
        fetchImpl: async () => {
            requests += 1;
        }
    });
    await assistant.elements["#api-check"].listeners.click();

    assert.equal(requests, 0);
    assert.match(assistant.elements["#api-status"].textContent, /nicht erreichbar/);
});

test("keeps Cloud AI disabled when endpoint or Turnstile configuration is missing", async () => {
    let requests = 0;
    const assistant = createAssistant({
        aiApiBaseUrl: "https://gateway.example",
        fetchImpl: async () => {
            requests += 1;
        }
    });
    await assistant.elements["#cloud-ai-submit"].listeners.click();
    assert.equal(assistant.elements["#cloud-ai-submit"].disabled, true);
    assert.equal(requests, 0);
    assert.match(assistant.elements["#cloud-ai-status"].textContent, /nicht konfiguriert/);
});

test("does not send a cloud question without explicit per-request consent", async () => {
    let requests = 0;
    const assistant = createAssistant({
        aiApiBaseUrl: "https://gateway.example",
        turnstileSiteKey: "public-site-key-123",
        turnstileApi: { render() { throw new Error("should not render before consent"); } },
        fetchImpl: async () => {
            requests += 1;
        }
    });
    await assistant.elements["#cloud-ai-submit"].listeners.click();
    assert.equal(requests, 0);
    assert.match(assistant.elements["#cloud-ai-status"].textContent, /ausdrücklich zu/);
});

test("sends only the explicitly approved question with Access cookies and a one-use Turnstile token", async () => {
    const requests = [];
    let widgetOptions;
    const assistant = createAssistant({
        aiApiBaseUrl: "https://gateway.example",
        turnstileSiteKey: "public-site-key-123",
        turnstileApi: {
            render(_container, options) {
                widgetOptions = options;
                options.callback("one-use-token");
                return "widget-1";
            },
            reset(widgetId) {
                assert.equal(widgetId, "widget-1");
            }
        },
        fetchImpl: async (url, options) => {
            requests.push({ url, options });
            return {
                ok: true,
                headers: { get: () => "application/json" },
                json: async () => ({ answer: "<b>Antwort</b>", requestId: "mock-id" })
            };
        }
    });
    assistant.elements["#assistant-question"].value = "Wie plane ich meinen Tag?";
    assistant.elements["#cloud-ai-consent"].checked = true;
    await assistant.elements["#cloud-ai-submit"].listeners.click();
    await assistant.elements["#cloud-ai-submit"].listeners.click();

    assert.equal(widgetOptions.sitekey, "public-site-key-123");
    assert.equal(widgetOptions.action, "niknolig_assistant");
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, "https://gateway.example/v1/ai/responses");
    assert.equal(requests[0].options.method, "POST");
    assert.equal(requests[0].options.credentials, "include");
    assert.deepEqual(JSON.parse(requests[0].options.body), {
        question: "Wie plane ich meinen Tag?",
        locale: "de",
        consent: true,
        turnstileToken: "one-use-token"
    });
    assert.equal(assistant.elements["#assistant-answer"].textContent, "<b>Antwort</b>");
    assert.equal(assistant.elements["#assistant-question"].value, "");
    assert.equal(assistant.stored.size, 0);
    assert.equal(assistant.elements["#cloud-ai-status"].textContent,
        "Antwort erhalten. NIKNOLIG speichert deine Frage und Antwort nicht.");
});

test("retains the question and displays a safe message when Access rejects the cloud request", async () => {
    const assistant = createAssistant({
        aiApiBaseUrl: "https://gateway.example",
        turnstileSiteKey: "public-site-key-123",
        turnstileApi: {
            render(_container, options) {
                options.callback("one-use-token");
                return "widget-1";
            },
            reset() {}
        },
        fetchImpl: async () => ({
            ok: false,
            status: 401,
            headers: { get: () => "application/json" },
            json: async () => ({ error: "unauthorized" })
        })
    });
    assistant.elements["#assistant-question"].value = "Eine Frage";
    assistant.elements["#cloud-ai-consent"].checked = true;
    await assistant.elements["#cloud-ai-submit"].listeners.click();
    await assistant.elements["#cloud-ai-submit"].listeners.click();

    assert.equal(assistant.elements["#assistant-question"].value, "Eine Frage");
    assert.match(assistant.elements["#cloud-ai-status"].textContent, /Cloudflare-Anmeldung/);
    assert.equal(assistant.elements["#assistant-answer"].textContent, "");
});
