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

function createAssistant() {
    const selectors = [
        "#assistant-form",
        "#assistant-question",
        "#assistant-answer",
        "#interest-profile-form",
        "#interest-consent",
        "#interest-status",
        "#interest-clear"
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
        querySelector(selector) {
            return elements[selector];
        }
    };
    const script = fs.readFileSync(path.join(__dirname, "assistant.js"), "utf8");
    vm.runInNewContext(script, { document, localStorage });

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
