(() => {
    const profileStorageKey = "niknolig-interest-profile";
    const supportedInterests = new Set(["work", "devices", "transport", "everyday"]);
    const interestLabels = {
        work: "Arbeit",
        devices: "Geräte",
        transport: "Transport",
        everyday: "Alltag"
    };
    const answers = [
        {
            terms: ["strom", "elektro", "electric", "sicherung", "fuse", "steckdose", "socket", "kabel", "cable", "leitung", "wire", "brand", "fire", "rauch", "smoke", "funke", "spark", "schlag", "shock", "bypass", "überbrück"],
            text: "Elektro-Engel gibt keine Anleitung zu elektrischen Arbeiten, Reparaturen oder dem Umgehen von Schutzmaßnahmen. Bei Rauch, Funken, Stromschlag oder akuter Gefahr: Abstand halten, nichts berühren und den Notruf 112 wählen. Wende dich sonst an eine Elektrofachkraft."
        },
        {
            terms: ["datenschutz", "privacy", "privat", "private", "speicher", "storage", "löschen", "loeschen", "delete", "daten", "data"],
            text: "Dein Interessenprofil ist optional und bleibt in diesem Browser. Du kannst es jederzeit ausschalten oder vollständig löschen. Fragen an diese lokale Vorschau werden nicht gespeichert."
        },
        {
            terms: ["bluetooth", "gerät", "geraet", "device", "maschine", "machine", "verbinden", "connect", "scan", "suche", "search"],
            text: "Die Android-Vorschau kann ausgesendete Bluetooth-Geräte in der Nähe suchen. Sie verbindet sich nicht mit ihnen und liest oder steuert keine Gerätewerte. Eine Suche dauert höchstens 30 Sekunden."
        },
        {
            terms: ["transport", "fahrt", "trip", "ziel", "destination", "termin", "appointment"],
            text: "Als allgemeine Planungshilfe kannst du Ziel, Zeitfenster und benötigte Materialien notieren und die Angaben selbst mit allen Beteiligten abstimmen."
        },
        {
            terms: ["arbeit", "aufgabe", "task", "plan", "beruf", "work", "job"],
            text: "Als allgemeine Planungshilfe kannst du Ziel, benötigte Informationen und Materialien sowie deinen selbst gewählten nächsten Schritt notieren."
        },
        {
            terms: ["alltag", "everyday", "erinner", "remind", "liste", "list", "organis", "organiz"],
            text: "Du kannst Aufgaben nach deinen eigenen Prioritäten ordnen. Diese Vorschau speichert keine Aufgabenliste oder Erinnerungen."
        }
    ];

    const questionForm = document.querySelector("#assistant-form");
    const questionInput = document.querySelector("#assistant-question");
    const answerOutput = document.querySelector("#assistant-answer");
    const profileForm = document.querySelector("#interest-profile-form");
    const consentInput = document.querySelector("#interest-consent");
    const interestInputs = [...profileForm.querySelectorAll('input[name="interest"]')];
    const interestStatus = document.querySelector("#interest-status");
    const clearButton = document.querySelector("#interest-clear");
    const apiCheckButton = document.querySelector("#api-check");
    const apiStatus = document.querySelector("#api-status");
    const cloudConsent = document.querySelector("#cloud-ai-consent");
    const cloudSubmitButton = document.querySelector("#cloud-ai-submit");
    const cloudStatus = document.querySelector("#cloud-ai-status");
    const turnstileContainer = document.querySelector("#cloud-turnstile");
    const cloudApiBaseUrl = window.NIKNOLIG_AI_API_BASE_URL || "";
    const turnstileSiteKey = window.NIKNOLIG_TURNSTILE_SITE_KEY || "";
    let turnstileWidgetId = null;
    let turnstileToken = "";
    let turnstileLoading = false;

    const cloudConfigurationValid = (() => {
        if (!cloudApiBaseUrl.trim() || !turnstileSiteKey.trim()) return false;
        try {
            const url = new URL(cloudApiBaseUrl);
            return url.protocol === "https:" &&
                url.origin === cloudApiBaseUrl.replace(/\/+$/, "") &&
                !url.username &&
                !url.password &&
                !url.search &&
                !url.hash &&
                /^[A-Za-z0-9_-]{8,256}$/.test(turnstileSiteKey);
        } catch {
            return false;
        }
    })();

    cloudSubmitButton.disabled = !cloudConfigurationValid;
    if (cloudConfigurationValid) {
        cloudStatus.textContent = "Cloud-KI ist verfügbar, sobald du die einzelne Übertragung ausdrücklich freigibst. Deine Frage wird nicht gespeichert.";
    }

    const selectedInterests = () => interestInputs
        .filter((input) => input.checked && supportedInterests.has(input.value))
        .map((input) => input.value);

    const setProfileStatus = (interests, enabled) => {
        if (!enabled) {
            interestStatus.textContent = "Interessenprofil aus; es sind keine Themen gespeichert.";
        } else if (interests.length === 0) {
            interestStatus.textContent = "Profil aktiv. Es wurden keine Interessengebiete ausgewählt.";
        } else {
            const labels = interests.map((interest) => interestLabels[interest]);
            interestStatus.textContent = `Profil aktiv in diesem Browser. Gespeicherte Themen: ${labels.join(", ")}.`;
        }
    };

    const removeProfile = () => {
        try {
            localStorage.removeItem(profileStorageKey);
            return true;
        } catch {
            return false;
        }
    };

    let savedInterests = [];
    let profileEnabled = false;
    let storageAvailable = true;
    try {
        const stored = JSON.parse(localStorage.getItem(profileStorageKey) || "null");
        if (stored?.enabled === true && Array.isArray(stored.interests)) {
            savedInterests = stored.interests.filter((interest) => supportedInterests.has(interest));
            profileEnabled = true;
        }
    } catch {
        storageAvailable = false;
        interestStatus.textContent = "Der lokale Speicher ist nicht verfügbar. Das Interessenprofil bleibt aus.";
    }
    if (profileEnabled) {
        consentInput.checked = true;
        interestInputs.forEach((input) => {
            input.checked = savedInterests.includes(input.value);
        });
    }
    if (storageAvailable) setProfileStatus(savedInterests, profileEnabled);

    consentInput.addEventListener("change", () => {
        if (!consentInput.checked) {
            const removed = removeProfile();
            interestInputs.forEach((input) => {
                input.checked = false;
            });
            profileEnabled = false;
            interestStatus.textContent = removed
                ? "Zustimmung widerrufen; das Interessenprofil wurde gelöscht."
                : "Der lokale Speicher ist nicht verfügbar. Prüfe die Speichereinstellungen deines Browsers.";
        }
    });

    profileForm.addEventListener("submit", (event) => {
        event.preventDefault();
        if (!consentInput.checked) {
            const removed = removeProfile();
            profileEnabled = false;
            interestInputs.forEach((input) => {
                input.checked = false;
            });
            interestStatus.textContent = removed
                ? "Interessenprofil aus; es sind keine Themen gespeichert."
                : "Der lokale Speicher ist nicht verfügbar. Prüfe die Speichereinstellungen deines Browsers.";
            return;
        }

        const interests = selectedInterests();
        try {
            localStorage.setItem(profileStorageKey, JSON.stringify({ enabled: true, interests }));
            savedInterests = interests;
            profileEnabled = true;
            setProfileStatus(interests, true);
        } catch {
            profileEnabled = false;
            savedInterests = [];
            storageAvailable = false;
            consentInput.checked = false;
            interestStatus.textContent = "Der lokale Speicher ist nicht verfügbar. Es wurden keine Themen gespeichert.";
        }
    });

    clearButton.addEventListener("click", () => {
        const removed = removeProfile();
        profileEnabled = false;
        savedInterests = [];
        consentInput.checked = false;
        interestInputs.forEach((input) => {
            input.checked = false;
        });
        interestStatus.textContent = removed
            ? "Interessenprofil und gespeicherte Themen wurden gelöscht."
            : "Der lokale Speicher ist nicht verfügbar. Prüfe die Speichereinstellungen deines Browsers.";
    });

    questionForm.addEventListener("submit", (event) => {
        event.preventDefault();
        const question = questionInput.value.trim().toLocaleLowerCase("de");
        const words = question.match(/\p{L}+/gu) || [];
        const matchedAnswer = answers.find(({ terms }) => words.some((word) => terms.some((term) => word.startsWith(term))));

        if (matchedAnswer) {
            answerOutput.textContent = matchedAnswer.text;
        } else {
            const fallback = "Dazu habe ich in der geprüften lokalen Wissensbasis keine passende Antwort. Stelle eine Frage zu NIKNOLIG oder nutze den technischen Support. Diese Vorschau erfindet keine Antwort.";
            const interests = profileEnabled ? savedInterests : [];
            answerOutput.textContent = interests.length
                ? `${fallback} Deine freiwillig gespeicherten Interessengebiete: ${interests.map((interest) => interestLabels[interest]).join(", ")}.`
                : fallback;
        }
        questionInput.value = "";
    });

    apiCheckButton.addEventListener("click", async () => {
        const configuredBaseUrl = window.NIKNOLIG_API_BASE_URL || "";
        if (!configuredBaseUrl.trim()) {
            apiStatus.textContent = "Serververbindung ist noch nicht konfiguriert.";
            return;
        }

        apiCheckButton.disabled = true;
        apiStatus.textContent = "Sichere Verbindung wird geprüft …";
        try {
            const baseUrl = new URL(configuredBaseUrl);
            if (
                baseUrl.protocol !== "https:" ||
                baseUrl.username ||
                baseUrl.password ||
                baseUrl.search ||
                baseUrl.hash
            ) {
                throw new Error("Invalid API URL");
            }
            const response = await fetch(`${configuredBaseUrl.replace(/\/+$/, "")}/v1/health`, {
                method: "GET",
                mode: "cors",
                credentials: "omit",
                cache: "no-store",
                redirect: "error",
                referrerPolicy: "no-referrer",
                headers: { Accept: "application/json" }
            });
            const contentType = response.headers.get("content-type") || "";
            const payload = await response.json();
            const healthy = response.ok &&
                contentType.toLowerCase().startsWith("application/json") &&
                payload.status === "ok" &&
                payload.service === "niknolig-api" &&
                payload.apiVersion === 1;
            apiStatus.textContent = healthy
                ? "HTTPS-Verbindung zum NIKNOLIG-Server ist verfügbar."
                : "Server nicht erreichbar oder Antwort ungültig. Fragen und Interessen wurden nicht übertragen.";
        } catch {
            apiStatus.textContent = "Server nicht erreichbar oder Antwort ungültig. Fragen und Interessen wurden nicht übertragen.";
        } finally {
            apiCheckButton.disabled = false;
        }
    });

    const loadTurnstile = () => {
        if (window.turnstile) {
            renderTurnstile();
            return;
        }
        if (turnstileLoading) return;
        turnstileLoading = true;
        cloudStatus.textContent = "Sicherheitsprüfung wird geladen …";
        const script = document.createElement("script");
        script.src = "https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit";
        script.async = true;
        script.defer = true;
        script.onload = () => {
            if (!window.turnstile) {
                turnstileLoading = false;
                cloudStatus.textContent = "Sicherheitsprüfung nicht verfügbar. Die Frage wurde nicht übertragen.";
                return;
            }
            renderTurnstile();
        };
        script.onerror = () => {
            turnstileLoading = false;
            cloudStatus.textContent = "Sicherheitsprüfung nicht verfügbar. Die Frage wurde nicht übertragen.";
        };
        document.head.appendChild(script);
    };

    const renderTurnstile = () => {
        if (!window.turnstile || turnstileWidgetId !== null) return;
        turnstileLoading = false;
        turnstileWidgetId = window.turnstile.render(turnstileContainer, {
            sitekey: turnstileSiteKey,
            action: "niknolig_assistant",
            appearance: "interaction-only",
            callback: (token) => {
                if (typeof token !== "string" || token.length < 1 || token.length > 2_048) {
                    turnstileToken = "";
                    cloudStatus.textContent = "Sicherheitsprüfung fehlgeschlagen. Die Frage wurde nicht übertragen.";
                    return;
                }
                turnstileToken = token;
                cloudStatus.textContent = "Sicherheitsprüfung abgeschlossen. Mit deinem Klick wird nur diese Frage übertragen.";
            },
            "expired-callback": () => {
                turnstileToken = "";
                cloudStatus.textContent = "Sicherheitsprüfung abgelaufen. Bitte erneut bestätigen.";
            },
            "error-callback": () => {
                turnstileToken = "";
                cloudStatus.textContent = "Sicherheitsprüfung fehlgeschlagen. Die Frage wurde nicht übertragen.";
            }
        });
    };

    cloudSubmitButton.addEventListener("click", async () => {
        if (!cloudConfigurationValid) {
            cloudStatus.textContent = "Cloud-KI ist nicht konfiguriert. Die lokale Wissenshilfe funktioniert weiterhin.";
            return;
        }
        if (!cloudConsent.checked) {
            cloudStatus.textContent = "Bitte stimme der einmaligen Übertragung ausdrücklich zu. Es wurde nichts gesendet.";
            return;
        }
        const question = questionInput.value.trim();
        if (!question || [...question].length > 500) {
            cloudStatus.textContent = "Gib eine Frage mit höchstens 500 Zeichen ein.";
            return;
        }
        if (!turnstileWidgetId) {
            loadTurnstile();
            return;
        }
        if (!turnstileToken) {
            cloudStatus.textContent = "Bitte schließe zuerst die Sicherheitsprüfung ab.";
            return;
        }

        const token = turnstileToken;
        turnstileToken = "";
        cloudSubmitButton.disabled = true;
        cloudStatus.textContent = "Deine freigegebene Frage wird sicher übertragen …";
        try {
            const response = await fetch(`${cloudApiBaseUrl}/v1/ai/responses`, {
                method: "POST",
                mode: "cors",
                credentials: "include",
                cache: "no-store",
                redirect: "error",
                referrerPolicy: "no-referrer",
                headers: {
                    Accept: "application/json",
                    "Content-Type": "application/json"
                },
                body: JSON.stringify({
                    question,
                    locale: document.documentElement.lang === "en" ? "en" : "de",
                    consent: true,
                    turnstileToken: token
                }),
                signal: AbortSignal.timeout(25_000)
            });
            const contentType = response.headers.get("content-type") || "";
            const payload = contentType.toLowerCase().startsWith("application/json")
                ? await response.json()
                : null;
            if (!response.ok || typeof payload?.answer !== "string" ||
                payload.answer.trim().length === 0 || [...payload.answer].length > 4_000) {
                throw new Error("Cloud response unavailable");
            }
            answerOutput.textContent = payload.answer.trim();
            questionInput.value = "";
            cloudStatus.textContent = "Antwort erhalten. NIKNOLIG speichert deine Frage und Antwort nicht.";
        } catch {
            cloudStatus.textContent = "Keine Antwort erhalten. Prüfe die Cloudflare-Anmeldung und Verbindung oder nutze die lokale Wissenshilfe. Die Frage wurde nicht lokal gespeichert.";
        } finally {
            if (window.turnstile && turnstileWidgetId !== null) {
                try {
                    window.turnstile.reset(turnstileWidgetId);
                } catch {
                    turnstileToken = "";
                }
            }
            cloudSubmitButton.disabled = false;
        }
    });
})();
