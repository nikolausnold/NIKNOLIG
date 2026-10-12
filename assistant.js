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
})();
