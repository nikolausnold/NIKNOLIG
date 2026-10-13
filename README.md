# NIKNOLIG

Eigenständiges Android-Entwicklungsgerüst für NIKNOLIG und Elektro-Engel. Die App
bietet eine ausdrücklich gestartete BLE-Gerätesuche sowie einen separaten,
interaktiven Elektro-Engel-Bereich mit fest vorbereiteten Beispielen und einer
begrenzten lokalen Wissenshilfe. Es gibt keine generative KI, Verbindung,
Gerätevertrauen, Sicherheitsfreigaben oder produktive Gerätefunktionen.

## Build-Basis

- JDK 17
- Gradle Wrapper 8.13
- Android Gradle Plugin 8.11.1
- Kotlin Android Plugin 2.2.21
- Android SDK / compile SDK 36

Debug-Build starten:

```sh
./gradlew :app:assembleDebug
```

Die Elektro-Engel-Beispiele und die Themenzuordnung der lokalen Wissenshilfe sind
als Android-Unit-Tests abgedeckt:

```sh
./gradlew :app:testDebugUnitTest
```

Der [Android-Build-Workflow](https://github.com/nikolausnold/NIKNOLIG/actions/workflows/android-build.yml)
erstellt und prüft die Debug-APK. Nach einem erfolgreichen Lauf findest du das
Artifact `niknolig-debug-<run_id>-<run_attempt>` unter **Artifacts** auf der
Workflow-Run-Seite. Es enthält
`app/build/outputs/apk/debug/app-debug.apk` und ist 14 Tage verfügbar.

### Android-Vorschau herunterladen und installieren

Öffne den [Android-Build-Workflow](https://github.com/nikolausnold/NIKNOLIG/actions/workflows/android-build.yml),
wähle den neuesten erfolgreichen Lauf und lade unter **Artifacts** das
`niknolig-debug-…`-Archiv herunter. Entpacke es und installiere
`app-debug.apk` auf einem Android-Gerät ab Android 8.0 (API 26). Android kann
bestätigen lassen, dass du Installationen aus der verwendeten Download-App
zulässt; erteile diese Freigabe nur für eine Quelle, der du vertraust.

Falls noch kein erfolgreicher Lauf vorliegt, muss der Build zuerst auf GitHub
Actions freigegeben und erfolgreich abgeschlossen werden. Der Debug-Download ist
eine Vorschau, keine Play-Store-Version. Er baut, scannt höchstens 30 Sekunden
nach BLE-Geräten und zeigt die Ergebnisse nach Stoppen der Suche weiter an. Eine
neue Suche oder das Verlassen des Bildschirms löscht die Ergebnisse. Es gibt
weiterhin keine Geräteverbindung oder Gerätesteuerung.

### Checkliste für den Gerätetest

Die Debug-APK ist für einen manuellen Funktionstest der Vorschau gedacht. Prüfe
mindestens ein echtes Gerät mit Android 12 oder höher; wenn verfügbar, teste
zusätzlich Android 8 bis 11. Notiere Modell, Android-Version und Ergebnis, aber
keine Geräteadressen oder personenbezogenen Daten.

- [ ] App frisch installieren und starten; Gerätesuche und Elektro-Engel-Vorschau
  lassen sich öffnen.
- [ ] BLE-Berechtigung beim ersten Suchstart erlauben. Suche läuft, zeigt
  ausgesendete Namen und Signalstärken, lässt sich manuell stoppen und startet
  nach erneutem Antippen wieder.
- [ ] Suche ohne Berechtigung ablehnen; verständlicher Hinweis erscheint und die
  App bleibt bedienbar. Berechtigung in Android-Einstellungen nachträglich
  erlauben und Suche erneut testen.
- [ ] Bluetooth ausschalten oder ein Gerät ohne BLE verwenden; App zeigt einen
  Fehlerzustand statt abzustürzen. Mit eingeschaltetem Bluetooth mindestens
  einen bekannten BLE-Werbenden in der Nähe suchen.
- [ ] Suche bis zum automatischen Ende laufen lassen; sie stoppt nach höchstens
  30 Sekunden. Eine neue Suche und das Verlassen der Ansicht löschen alte
  Ergebnisse; manuelles Stoppen lässt die aktuelle Ergebnisliste stehen.
- [ ] Während einer Suche zu einer anderen App wechseln oder die Activity
  verlassen; Scan endet. Zurückkehren und eine neue Suche starten.
- [ ] WLAN/Mobilfunk deaktivieren; Gerätesuche und vorbereitete lokale
  Elektro-Engel-Beispiele bleiben verfügbar. Internetstatus nicht mit
  Serververfügbarkeit verwechseln.
- [ ] Bei Bedarf Serverprüfung testen: ohne konfigurierte Serveradresse bleibt
  sie deaktiviert; mit gültiger HTTPS-Basisadresse meldet sie nur den
  `/v1/health`-Status.

Das ist ein Test der Gerätesuche und Vorschau, keine Prüfung einer
Geräteverbindung: Die App verbindet sich nicht, liest oder schreibt keine
GATT-Werte und steuert keine Geräte.

## Android-Release-Build

Ein signiertes Android App Bundle wird beim Pushen eines Tags im Format
`vMAJOR.MINOR.PATCH` (zum Beispiel `v1.0.0`) gebaut und als Workflow-Artefakt
bereitgestellt. Die Tag-Version wird als App-Version übernommen; eine aufsteigende
Buildnummer wird als Android-`versionCode` gesetzt. Dafür muss zuerst die
[GitHub-Umgebung `android-release`](https://github.com/nikolausnold/NIKNOLIG/settings/environments)
eingerichtet und dort müssen diese vier **Environment Secrets** hinterlegt sein:

- `ANDROID_KEYSTORE_BASE64`: Base64-kodierter Upload-Keystore
- `ANDROID_KEYSTORE_PASSWORD`: Keystore-Passwort
- `ANDROID_KEY_ALIAS`: Alias des Upload-Schlüssels
- `ANDROID_KEY_PASSWORD`: Passwort des Upload-Schlüssels

Konfiguriere dort erforderliche Reviewer (mindestens eine unabhängige Freigabe),
verhindere Selbstfreigabe und Admin-Bypass, sofern die Repository-Optionen das
anbieten, und beschränke erlaubte Deployments auf Tags `v*`. Hinterlege die
Secrets ausschließlich als Environment Secrets, nicht als Repository Secrets.
Bis diese Einstellungen vorgenommen und geprüft sind, ist die Release-Umgebung
nicht als geschützt zu betrachten. Mit aktivem Review-Schutz wartet der Release-Job
vor dem Zugriff auf die Signierschlüssel auf die Freigabe.

Der Release-Workflow erstellt und prüft ein AAB-Artefakt; er lädt nichts in die
Play Console hoch und veröffentlicht nichts. Der Workflow prüft die Signatur des
AAB. Der Keystore wird nur während
des CI-Laufs in einem temporären Runner-Verzeichnis abgelegt; Schlüssel und
Passwörter gehören weder ins Repository noch in Build-Logs. Ohne diese Secrets
oder eine gültige PKCS12-Datei mit passendem Alias bricht der Release-Workflow
ab. Ein lokaler `bundleRelease`-Build ohne
Signierungsvariablen ist nicht für einen Store-Upload verwendbar.

### Upload-Keystore sicher erstellen

Erzeuge den Upload-Schlüssel selbst auf einem vertrauenswürdigen privaten Rechner,
nicht in CI, nicht in diesem Repository und nicht im Chat. Das folgende Beispiel
fragt Keystore- und Schlüsselpasswörter interaktiv ab; gib sie nicht als
Kommandozeilenargument ein:

```sh
umask 077
mkdir -p "$HOME/.android/keystores"
chmod 700 "$HOME/.android/keystores"
keytool -genkeypair -v \
  -keystore "$HOME/.android/keystores/niknolig-upload.p12" \
  -storetype PKCS12 -alias niknolig-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Lege eine verschlüsselte Offline-Sicherung des Keystores an. Erzeuge für das
Secret eine geschützte Base64-Datei außerhalb des Repositories und entferne sie
nach dem Übertragen in GitHub:

```sh
umask 077
set -o pipefail
base64 < "$HOME/.android/keystores/niknolig-upload.p12" \
  | tr -d '\n' \
  > "$HOME/.android/keystores/niknolig-upload.p12.b64"
chmod 600 "$HOME/.android/keystores/niknolig-upload.p12.b64"
```

Richte in der obigen `android-release`-Umgebung unter **Environment secrets**
`ANDROID_KEYSTORE_BASE64` mit dem Inhalt der `.p12.b64`-Datei,
`ANDROID_KEYSTORE_PASSWORD` mit dem Keystore-Passwort, `ANDROID_KEY_ALIAS` mit
`niknolig-upload` und `ANDROID_KEY_PASSWORD` mit dem Passwort des Schlüssels ein.
Bei keytool kannst du dasselbe Passwort für Keystore und Schlüssel verwenden.
Übertrage die Werte nur direkt in GitHub und poste sie nirgendwo sonst. Der
Workflow prüft Keystore, Passwort und Alias, bevor Gradle läuft. Erzeuge das
öffentliche Upload-Zertifikat für Play App Signing mit:

```sh
keytool -export -rfc \
  -keystore "$HOME/.android/keystores/niknolig-upload.p12" \
  -alias niknolig-upload \
  -file "$HOME/.android/keystores/niknolig-upload.pem"
```

Nur das öffentliche Zertifikat, nicht der Keystore, gehört in die Play Console.
Bei Verlust eines registrierten Upload-Schlüssels nutze den offiziellen
Wiederherstellungsweg in der Play Console.

Ein erfolgreich gebautes AAB ist noch keine Freigabe zur Veröffentlichung. Vor
einem Store-Release sind unter anderem Datenschutz- und Betreiberangaben,
Store-Eintrag und Daten-Sicherheitsdeklaration sowie Tests auf unterstützten
Geräten und eine fachliche Sicherheitsfreigabe erforderlich. Die App bietet
derzeit nur eine einfache BLE-Suche, keine produktive Geräteverbindung oder
Gerätesteuerung.

### Play-Console-Checkliste

- [ ] Play App Signing aktivieren und den öffentlichen Upload-Schlüssel registrieren.
- [ ] Anbieter/Rechtsträger, Supportkontakt und erforderliche Datenschutz- und
  Betreiberangaben vervollständigen. Die [Datenschutzerklärung](privacy.html)
  beschreibt aktuell nur den Prototyp und muss vor Einreichung rechtlich und
  technisch abgeglichen werden.
- [ ] Das [Play-Datenformular](https://support.google.com/googleplay/android-developer/answer/10787469)
  (für jede App verpflichtend) mit der finalen App, allen Bibliotheken und der
  tatsächlichen Verarbeitung von Support-E-Mails abgleichen. Die Suche bleibt auf
  dem Gerät; freiwillig
  gesendete Support-E-Mails erreichen dennoch den Support-Empfänger. Nicht
  ungeprüft „keine Daten“ deklarieren.
- [ ] Store-Eintrag, Altersfreigabe, Zielgruppe, Werbung, App-Zugriff,
  Screenshots und Länder/Verfügbarkeit in der Play Console ausfüllen.
- [ ] Auf unterstützten echten Geräten Berechtigungszustände, Ablehnung,
  Bluetooth-Aus, Scan-Timeout, Stoppen und E-Mail-/Datenschutzlinks testen.
- [ ] Falls das Play-Console-Konto ein neues persönliches Entwicklerkonto ist,
  dessen [geschlossene Testanforderung](https://support.google.com/googleplay/android-developer/answer/14151465)
  erfüllen (derzeit mindestens 12 Tester, 14 Tage durchgehend angemeldet); diese
  Vorgabe hängt von Kontotyp und Erstellungsdatum ab.
- [ ] Die [Google-Play-Target-API-Vorgabe](https://developer.android.com/google/play/requirements/target-sdk)
  zum Einreichungszeitpunkt erneut prüfen; der Build zielt derzeit auf API 36.

## Support

Die App bietet technischen Support per E-Mail an **n_nold@hotmail.com**. Zahlungsfragen
werden nicht über diesen Support bearbeitet; bitte wende dich dafür an den
Zahlungsanbieter oder Händler. Sende keine Passwörter oder Zahlungsdaten an den
technischen Support.

## Website

Die statische Website liegt in `index.html` und `styles.css`. Sie benötigt weder
einen Build-Schritt noch externe JavaScript-Bibliotheken. Für günstiges Hosting kann
GitHub Pages verwendet werden. Der Workflow
[`Deploy website`](https://github.com/nikolausnold/NIKNOLIG/actions/workflows/deploy-website.yml)
veröffentlicht bei Änderungen an der Website auf `main` `index.html`,
`privacy.html`, `styles.css`, `robots.txt`, `sitemap.xml` und die Website-Assets;
ein manueller
Start ist ebenfalls möglich. Das lautlose, zehnsekündige NIKNOLIG-Intro ist als
SVG/CSS-Animation in `assets/niknolig-intro.svg` umgesetzt, wird von GitHub Pages
mitveröffentlicht und benötigt keine hochgeladene MP4-Datei.
Bei aktivierter Einstellung für reduzierte Bewegung wird die Animation statisch
als vollständiges Logo dargestellt. Aktiviere unter
[Repository-Einstellungen → Pages](https://github.com/nikolausnold/NIKNOLIG/settings/pages)
als Veröffentlichungsquelle **GitHub Actions**. Nach einem erfolgreichen Lauf zeigt
der Workflow die veröffentlichte URL an. `robots.txt` und `sitemap.xml` werden
mitveröffentlicht, damit Suchmaschinen die Website finden können. Für eine
Indexierungsanfrage kannst du die Website in
[Google Search Console](https://search.google.com/search-console/) hinzufügen und
dort die Sitemap `https://nikolausnold.github.io/NIKNOLIG/sitemap.xml` einreichen.
Verfügbarkeit und Kosten hängen von Repository-Sichtbarkeit und GitHub-Tarif ab.
Die statische, lokale Wissenshilfe liegt in `assistant.js`; vor dem Hochladen prüft
der Pages-Workflow sie mit den integrierten Node.js-Tests.

### Optionale Server-Verfügbarkeitsprüfung

`server/health-server.mjs` stellt ohne zusätzliche Pakete ausschließlich
`GET /v1/health` bereit. Es gibt keine Konten, Nutzdaten-Endpunkte oder
Anfrageprotokollierung. Der optionale Client-Aufruf muss vom Nutzer gestartet werden
und überträgt keine Fragen oder Interessen; Server/Netzwerkinfrastruktur sieht dabei
die IP-Adresse und technische Verbindungsmetadaten. Ohne Konfiguration ist die
Prüfung deaktiviert.

Für lokale API-Tests:

```sh
node --test server/health-server.test.mjs
```

Für den Betrieb `node server/health-server.mjs` hinter einem vertrauenswürdigen
TLS-Reverse-Proxy ausführen. Der Dienst bindet standardmäßig nur an `127.0.0.1`;
bei Containerbetrieb den internen Host über `HOST` konfigurieren und TLS am
Ingress/Proxy erzwingen. Setze dort `NIKNOLIG_ALLOWED_ORIGIN` exakt auf
`https://nikolausnold.github.io`, damit nur die Website browserseitig auf die
Statusantwort zugreifen kann. `PORT` steuert den internen Listener.

Die öffentliche Basisadresse ist kein Geheimnis. Für Android-Builds setze unter
[Repository-Variablen](https://github.com/nikolausnold/NIKNOLIG/settings/variables/actions)
die GitHub-Actions-Variable `NIKNOLIG_API_BASE_URL` auf die HTTPS-Basisadresse;
Gradle lehnt HTTP, Zugangsdaten, Query und Fragment ab. Für die Website wird dieselbe
Repository-Variable beim Pages-Deploy in `api-config.js` geschrieben und ebenfalls
auf HTTPS validiert. Setze sie erst auf einen realen, mit gültigem Zertifikat
geschützten Server. Der Health-Endpunkt ist nur die Verbindungsgrundlage; Kontozugang,
Authentifizierung und zahlungsabhängige Berechtigungen sind nicht enthalten.

### OpenAI-KI-Integration: zentrales Gateway vorbereitet, Produktstart BLOCKIERT

Ein zentraler Cloudflare Worker unterstützt versionierte, per Cloudflare-Access-
Audience getrennte App-Profile, serverseitig konfigurierbare Modelle,
browserseitiges Turnstile und Durable-Object-Quoten. Das Gateway ist lokal mit
Mocks getestet, aber weder live verifiziert noch deployed. Der Pages-Assistent
und Android bleiben lokal/offline-first; sie senden keine Fragen. Access-Flow,
Worker-Konfiguration/Secrets, Kostenkontrollen sowie Datenschutz- und
Sicherheitsfreigaben fehlen weiterhin. Turnstile, Quoten und CORS ersetzen keine
Authentifizierung. Ein OpenAI-Schlüssel gehört ausschließlich in Cloudflares
Secret Store, niemals in GitHub Pages, Website- oder Android-Code.

Worker-Architektur, Deployment-Schritte und verbleibende manuelle Freigaben:
[`docs/architecture/cloudflare-assistant-gateway.md`](docs/architecture/cloudflare-assistant-gateway.md).
Die aktualisierte Bestandsaufnahme bleibt hier:
[`docs/architecture/openai-assistant-readiness.md`](docs/architecture/openai-assistant-readiness.md).
Vor jeder Aktivierung müssen der bestehende Cloudflare-Worker und seine Assets
geprüft werden; die Defense-Policy und das getrennte Defense-Repository sind
ebenfalls noch zu bestätigen. Bis Deployment, Identitätsfluss,
Datenschutzhinweise und ausdrückliche Einwilligung freigegeben sind, bleibt die
Assistenz lokal.

### Offline-Status und künftige Synchronisierung

Die Android-App zeigt den vom Betriebssystem erkannten Internetstatus auf beiden
Bildschirmen an. „Internet verfügbar“ bedeutet nicht, dass der NIKNOLIG-Server
erreichbar ist; dafür bleibt die ausdrücklich gestartete Health-Prüfung getrennt.
Die gebündelte lokale Wissenshilfe und BLE-Gerätesuche benötigen diese Verbindung
nicht. Es gibt in diesem Stand weder einen dauerhaften Sync-Auftragsbestand noch
einen Wissensaustausch mit dem getrennten Defense-Projekt. Website-Inhalte sind
ohne ausdrücklich eingerichteten Offline-Cache nicht als offline verfügbar
garantiert.

Bestandsaufnahme, Statusbedeutungen und der ausschließlich vorgeschlagene
Schnittstellenvertrag stehen in
[`docs/architecture/offline-first-readiness.md`](docs/architecture/offline-first-readiness.md).
Der Vertrag ist nicht implementiert; er überträgt keine Fragen, Interessenprofile
oder Gerätekennungen.

Der Abschnitt **Elektro-Engel** stellt aktuelle App-Funktionen den Zukunftsideen
gegenüber. Die Android-App verlinkt zwischen Gerätesuche und Assistenzvorschau.
Die lokale Wissenshilfe in Website und App ordnet Fragen nach festen Schlüsselwörtern
vorbereiteten Antworten zu; sie ist keine generative KI, speichert keine Fragen und
sendet sie nicht an einen Server. Eine künftige OpenAI-Integration ist ausdrücklich
BLOCKIERT und darf erst nach den dokumentierten Backend-, Datenschutz- und
Sicherheitsfreigaben aktiviert werden. Elektrische Arbeiten und das Umgehen von
Schutzmaßnahmen werden ausdrücklich nicht angeleitet. Unbekannte Fragen erhalten
keine erfundene Antwort.

Ein freiwilliges Interessenprofil kann ausschließlich allgemeine Themen (Arbeit,
Geräte, Transport, Alltag) speichern. Es bleibt nach ausdrücklicher Zustimmung
lokal im Browser oder im internen App-Speicher, wird für Vorschläge verwendet und
kann jederzeit widerrufen und gelöscht werden. Es gibt keine Beobachtung von
Aktivitäten und kein automatisches Lernen aus Fragen oder Nutzung. Das Profil kann
personenbezogen sein; die Datenschutzerklärung weist darauf hin.

Vor einer öffentlichen Veröffentlichung müssen die rechtlich erforderlichen
Betreiber- und Datenschutzangaben ergänzt und geprüft werden. Diese Angaben wurden
nicht erfunden oder als fertige Rechtsseiten ausgegeben.

## Entwicklungs- und Freigabestatus

Die Android-App besteht aus zwei nativen Activities mit gemeinsamer Navigation:
der NIKNOLIG-Gerätesuche und der Elektro-Engel-Vorschau samt lokaler Wissenshilfe. Die ausdrücklich
gestartete, auf den Vordergrund beschränkte BLE-Suche zeigt ausgesendete Gerätenamen
und Signalstärken nur vorübergehend an und verwendet Geräteadressen im Arbeitsspeicher
zum Deduplizieren; Adressen werden nicht angezeigt. Die Suche endet nach spätestens
30 Sekunden, beim Stoppen oder beim Verlassen der Ansicht. Ergebnisse bleiben nach
dem Stoppen bis zur nächsten Suche im Arbeitsspeicher sichtbar und werden beim
Verlassen der Ansicht gelöscht, nicht dauerhaft gespeichert. Es gibt keine
Geräteverbindung, keinen GATT-Zugriff,
kein Lesen oder Schreiben von Gerätewerten, keine Authentifizierung, Autorisierung oder
Gerätevertrauens- und Freigabeentscheidung. Auf Android 12 und höher werden
BLE-Scan- und Adapterberechtigungen angefordert. Die Adapterberechtigung ist für
den Zugriff auf den Bluetooth-Adapter nötig und wird nicht für eine
Geräteverbindung verwendet; auf älteren unterstützten Android-Versionen wird für
die Suche die Standortberechtigung benötigt. Jede Suche endet spätestens
nach 30 Sekunden, beim manuellen Stoppen oder beim Verlassen der Activity.

Automatisierte Unit-Tests prüfen die Elektro-Engel-Beispielkategorien, deren
Katalogeinträge und die lokale Themenzuordnung einschließlich Sicherheitsgrenzen.
Der Pages-Workflow prüft die Website-Wissenshilfe mit Node.js-Tests vor dem Upload.
BLE-Zustandsübergänge, Berechtigungen und Fehlerzustände sind
weiterhin nicht durch automatisierte Funktionstests abgedeckt. Der Android-Workflow
baut ein Debug-APK und prüft dessen Integrität; das ersetzt weder Funktionstests
auf echten Geräten noch eine SecurityChain-Freigabe. Vertrauen und Freigaben müssen
vor jeder künftigen Verbindung explizit entworfen und getestet werden.
KI-gestützte Experimente dürfen keine produktiven Sicherheitsregeln automatisch
verändern.

Das Projekt ist damit noch nicht releasefähig. Es gibt keine verifizierte
Produktionsfreigabe oder SecurityChain-P0-Testabdeckung.
