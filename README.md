# NIKNOLIG

Eigenständiges Android-Entwicklungsgerüst für NIKNOLIG und Elektro-Engel. Der
aktuelle Bildschirm ist eine Vorschau; BLE, Gerätevertrauen,
Sicherheitsfreigaben und produktive Funktionen sind noch nicht implementiert.

## Build-Basis

- JDK 17
- Gradle Wrapper 8.13
- Android Gradle Plugin 8.11.1
- Kotlin Android Plugin 2.2.21
- Android SDK / compile SDK 35

Debug-Build starten:

```sh
./gradlew :app:assembleDebug
```

Der [Android-Build-Workflow](https://github.com/nikolausnold/NIKNOLIG/actions/workflows/android-build.yml)
erstellt und prüft die Debug-APK. Nach einem erfolgreichen Lauf findest du das
Artifact `niknolig-debug-apk-<run_id>-<run_attempt>` unter **Artifacts** auf der
Workflow-Run-Seite. Es enthält
`app/build/outputs/apk/debug/app-debug.apk` und ist 14 Tage verfügbar.

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
veröffentlicht bei Änderungen an `index.html`, `styles.css` oder der
Workflowdatei auf `main` ein Artefakt mit diesen beiden Dateien und
`assets/niknolig-mark.svg`. Änderungen nur an der SVG lösen keinen Lauf aus;
alternativ kann der Workflow manuell für `main` gestartet werden. Aktiviere unter
[Repository-Einstellungen → Pages](https://github.com/nikolausnold/NIKNOLIG/settings/pages)
als Veröffentlichungsquelle **GitHub Actions**. Nach einem erfolgreichen Lauf zeigt
der Workflow die veröffentlichte URL an. Verfügbarkeit und Kosten hängen von
Repository-Sichtbarkeit und GitHub-Tarif ab.

Vor einer öffentlichen Veröffentlichung müssen die rechtlich erforderlichen
Betreiber- und Datenschutzangaben ergänzt und geprüft werden. Diese Angaben wurden
nicht erfunden oder als fertige Rechtsseiten ausgegeben.

Die Website verlinkt im Bereich **Android-App** auf die offiziellen
[GitHub-Releases](https://github.com/nikolausnold/NIKNOLIG/releases). Dort steht
ein APK-Download bereit, sobald eine APK veröffentlicht wurde; aktuell ist noch
keine APK-Datei veröffentlicht.

## Android-Releases

Der [Android-Build-Workflow](https://github.com/nikolausnold/NIKNOLIG/actions/workflows/android-build.yml)
erstellt bei jedem Push und Pull Request weiterhin ein geprüftes Debug-APK. Für einen
Release wird ein Tag im Format `vMAJOR.MINOR.PATCH` auf einem Commit von `main`
gepusht. Der Workflow baut daraus ein signiertes APK und AAB, prüft beide Dateien
und veröffentlicht sie samt SHA-256-Prüfsummen als GitHub Release.

Vor dem ersten Release müssen in den Repository-Secrets
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` und
`ANDROID_KEY_PASSWORD` hinterlegt werden. `ANDROID_KEYSTORE_BASE64` enthält den
Base64-kodierten Inhalt des privaten Android-Keystores. Der Keystore und seine
Passwörter dürfen nicht ins Repository eingecheckt werden; derselbe Keystore muss
für alle späteren App-Updates sicher aufbewahrt werden.

## Lernkonzept für Elektro-Engel (Vorbereitung)

Elektro-Engel soll mit der Zeit dazulernen, was für hilfreiche Unterstützung nötig
ist. Das ist ein schrittweises Entwicklungsziel. Die Android-Vorschau enthält nun
ein begrenztes lokales Lernprofil, aber noch kein selbstlernendes Modell. Die
Vorbereitung trennt drei Bereiche:

**Leitlinie:** Menschen fühlen; Maschinen können viel, sollen aber Menschen
unterstützen und nicht über sie bestimmen. Elektro-Engel beginnt mit kurzen
Antworten, fragt bei Unsicherheit nach und behandelt wichtige Themen vorsichtig.
Eine einzelne Rückmeldung oder Nutzungsänderung passt Antworten nicht automatisch
an.

- **Nutzungsmuster:** freiwillig freigegebene, möglichst grobe und nicht sensible
  Muster, die über „genutzt/nicht genutzt“ hinausgehen können: etwa wiederkehrende
  Schritte innerhalb einer Funktion, Stellen, an denen Abläufe abgebrochen oder
  wiederholt werden, freiwillige Korrekturen sowie grobe Rückmeldungen dazu, ob eine
  Antwort hilfreich oder unklar war. So lassen sich Bedienhürden und gewünschte
  Erklärungsweisen erkennen, ohne einzelne Personen zu bewerten.
- **Nutzerpräferenzen:** ausdrücklich freigegebene Einstellungen, die Antworten
  persönlicher machen. Sie bleiben vom allgemeinen Fachwissen getrennt.
- **Fachwissen:** neue Informationen und Verbesserungsvorschläge, die mit ihrer
  Quelle und ihrem Prüfstatus erfasst und vor einer Nutzung überprüft werden.

Für Nutzungsmuster gilt Datenminimierung: keine Gesprächsinhalte oder Rohtexte,
Passwörter, Zahlungsdaten, genauen Standort- oder Gerätekennungen und keine
Ableitung sensibler Merkmale. Erhebung setzt eine verständliche Einwilligung voraus
und muss abschaltbar sein; Zweck, Speicherdauer, Einsicht und Löschung sind vor einer
Implementierung festzulegen. Auch scheinbar harmlose oder zusammengefasste Daten
werden nicht automatisch als anonym behandelt. Erfasst werden sollen nur
zweckgebundene, abstrahierte Interaktionsmerkmale; daraus dürfen keine Diagnosen,
Emotionen, Persönlichkeit oder andere sensible Eigenschaften abgeleitet werden.
Mehr Kontext ist nicht automatisch besser: Jede zusätzliche Signalart braucht einen
erklärten Zweck und eine eigene Prüfung auf Datenschutzrisiken.

Vorgesehener Ablauf für neue Erkenntnisse: **sammeln → getrennt prüfen → gezielt
freigeben → bei Bedarf zurücknehmen**. Nutzerfeedback oder automatisch erzeugte
Vorschläge ändern weder produktives Verhalten noch Sicherheitsregeln selbstständig.
Gerätevertrauen, Berechtigungen und Geräteaktionen bleiben unabhängig vom Lernsystem
und erfordern später eigene, explizite Freigaben.

Vor einer produktiven Ausweitung müssen mindestens Einwilligung und Widerruf, Einsicht,
Änderung und Löschung gespeicherter Präferenzen sowie Datenminimierung und
Aufbewahrungsfristen festgelegt werden. Die aktuelle Android-Vorschau speichert eine
bewusst gewählte Erklärungsart und zählt nach gesondertem Opt-in ausschließlich
manuelle Rückmeldungen zu Beispielantworten (hilfreich, unklar oder Korrektur nötig)
lokal zusammen. Korrekturtexte werden nicht gespeichert. Die Zähler lassen sich
zusammen mit der Präferenz löschen. Eine automatische Aufbewahrungsfrist gibt es
noch nicht; die Daten bleiben bis zur manuellen Löschung oder Deinstallation auf
dem Gerät. Es gibt keine automatische Verhaltensmessung, Übertragung,
Wikipedia-Import oder Modelltraining; die Zähler verändern Antworten nicht. Jede
Erweiterung braucht eine eigene Prüfung und Freigabe.

Sitzungsübergreifende Fortsetzung ist als manuell verfasste, lokal gespeicherte
Zusammenfassung mit maximal 500 Zeichen verfügbar. Sie wird nur nach ausdrücklichem
Speichern abgelegt, bleibt änderbar und wird beim Löschen des Lernprofils entfernt.
Die Vorschau verwendet sie noch nicht automatisch und speichert keine vollständigen
Gesprächsverläufe. Nutzer werden darauf hingewiesen, keine sensiblen Angaben
einzutragen.

### Wikipedia als Wissensgerüst

Für den ersten Wissens-Prototyp kann Wikipedia als navigierbare Ausgangsbasis
dienen, nicht als ungeprüfte Trainingssammlung oder alleinige Wahrheitsquelle.
Vorgesehen ist eine kuratierte Themenauswahl statt eines vollständigen Dumps.
Zu jedem übernommenen Eintrag gehören mindestens Artikeltitel, direkte URL,
Revisionsstand und Abrufdatum sowie Prüfstatus und Lizenz-/Attributionshinweise.
So bleiben Aussagen auf ihre Quelle zurückführbar und Änderungen am Artikel
erkennbar.

Neue Inhalte bleiben zunächst **ungeprüft**. Erst nach fachlicher Prüfung dürfen
sie als begrenzte, zitierbare Wissensnotiz vorgeschlagen werden; Änderungen an der
Wikipedia-Seite setzen die Prüfung zurück. Sicherheitskritische, medizinische,
rechtliche oder aktuelle Aussagen brauchen zusätzlich belastbare Fachquellen und
dürfen nie allein aus Wikipedia abgeleitet werden. Wikipedia-Inhalte werden nicht
automatisch in ein Modelltraining übernommen und verändern weder Regeln noch
Geräteaktionen.

Bei jeder späteren Wiederverwendung müssen die Lizenz der konkreten Inhalte und
die erforderliche Namensnennung, Lizenzverlinkung, Kennzeichnung von Änderungen
und Weitergabeauflagen geprüft und eingehalten werden. Als Einstieg dienen
[Wikipedia: Inhalte wiederverwenden](https://de.wikipedia.org/wiki/Wikipedia:Weiternutzung)
und die [MediaWiki-Dokumentation zur Wiederverwendung von Inhalten](https://www.mediawiki.org/wiki/Wikimedia_APIs/Content_reuse).
Ein Abruf oder Import ist derzeit nicht implementiert.

## Isolierte Backend-Grundlage (Sandbox, nicht produktiv)

Die bestehende App verwendet native Activities, XML-Oberflächen und lokale
`SharedPreferences`. Assistenteingaben werden nur angezeigt; Stil, Feedback und
Sitzungszusammenfassung bleiben lokal. Es gab bisher weder einen API-Client noch
einen Server. Das neue Backend ergänzt ausschließlich eine unabhängig betreibbare
Testgrundlage, keine KI, Gerätefreigaben, Zahlungen oder Nutzerdatenverarbeitung.
NIKNOLIG Defense und andere Repositories werden nicht berührt.

### Architektur und API-Vertrag

`backend/` verwendet Python 3.12 und die Standardbibliothek, SQLite und versionierte
SQL-Migrationen. Es gibt keine zusätzlichen Python-Pakete oder kostenpflichtigen
Dienste. Für Android kommt nur JUnit 4.13.2 als Testabhängigkeit hinzu.
Der maschinenlesbare Vertrag liegt in [`backend/openapi.json`](backend/openapi.json).
Inkompatible API-Änderungen benötigen eine neue Hauptversion statt Änderungen an `/v1`.

| Methode und Pfad | Zweck | Zugriff |
| --- | --- | --- |
| `GET /v1/health` | Bereitschaft einschließlich Datenbankprüfung, API-Version und Sandboxmodus | Öffentlich, nur im isolierten Netz |
| `GET /v1/capabilities` | Explizit deaktivierte KI, Profilsynchronisierung und Geräteaktionen | Öffentlich, nur im isolierten Netz |
| `GET /v1/sandbox/profile` | Synthetisches Testprofil lesen | `profile:read` |
| `PUT /v1/sandbox/profile` | Synthetischen Erklärungsstil setzen | `profile:write` |
| `DELETE /v1/sandbox/profile` | Synthetisches Testprofil löschen | `profile:write` |

Das einzige Profilfeld ist `responseStyle`: `concise`, `step_by_step` oder `detailed`.
Keine Freitexte, Gesprächsinhalte, Zusammenfassungen, Feedbackzähler, Gerätekennungen
oder Bankdaten gehören in die Datenbank. Die Android-App nutzt die Profilrouten
bewusst **nicht**. Keine automatische Migration oder Synchronisierung lokaler Daten.
Validierungsfehler und nicht unterstützte Aufrufe liefern begrenzte JSON-Fehler;
interne Ausnahmen werden nicht an Clients ausgegeben.

### Zugriffsschutz und Grenzen

Die Sandbox nutzt separat provisionierte zufällige Bearer-Tokens, ausschließlich
für synthetische Testsubjekte. Die Betreiberkonfiguration enthält nur SHA-256-Digests,
Subjektzuordnung und erlaubte Scopes, keine Klartexttokens. Das Subjekt wird aus der
geprüften Konfiguration bestimmt, nie aus einem Requestparameter. Ein Token darf
keine anderen Subjekte auswählen. Fehlende Authentifizierungskonfiguration sperrt
alle Profilrouten; es gibt keine Standardzugangsdaten, Registrierung oder Tokenausgabe.
Ein SHA-256-Digest ersetzt dabei keine sichere Tokenentropie.

Die optionale Datei `AUTH_CONFIG_FILE` enthält ein JSON-Array mit exakt
`tokenSha256`, `subject` und `scopes` je Eintrag. Subjekte müssen
`sandbox-[a-z0-9][a-z0-9_-]{0,55}` entsprechen, Digests aus 64 kleinen Hexzeichen
bestehen und Scopes eine nichtleere, duplikatfreie Auswahl aus `profile:read` und
`profile:write` sein. Die Datei wird beim Start gelesen; Rotation oder Widerruf
erfordern eine geänderte Konfiguration und einen Neustart. Tokens nur außerhalb
dieses Repositories kryptografisch zufällig provisionieren, niemals in der App
speichern. Auch rein technische Zugriffstests dürfen nur synthetische Subjekte verwenden.

| Laufzeitvariable | Vorgabe / Bedeutung |
| --- | --- |
| `DB_PATH` | Lokal `backend/data/sandbox.sqlite3`, im Container `/data/sandbox.sqlite3` |
| `BIND_HOST` | Lokal `127.0.0.1`; im isolierten Container explizit `0.0.0.0` |
| `ALLOW_INTERNAL_CONTAINER_BIND` | Nur `1` erlaubt den internen Container-Bind |
| `PORT` | Interner HTTP-Port `8080`, keine Host-Portfreigabe |
| `AUTH_CONFIG_FILE` | Optionale digestbasierte Zugriffskonfiguration; ohne gültigen Inhalt bleiben Profilrouten gesperrt |

**Sicherheitsstopp vor echten Accounts:** Identitätsanbieter, Anmeldung, Tokenlaufzeit,
Widerruf, Aufbewahrung, Einwilligung und Rollenmodell müssen separat abgestimmt und
freigegeben werden. Sandbox-Tokens sind ausdrücklich **keine produktive
Nutzerauthentifizierung** und dürfen weder in APKs eingebaut noch mit echten Personen
verknüpft werden. Hierfür werden keine Zugangsdaten angefordert oder erzeugt.

Strukturierte Logs beschränken sich auf technische Requestmetadaten mit generierter
Request-ID und festen Routennamen. Keine Tokens, Requestinhalte, Nutzereingaben,
Subjekte, IP-Adressen oder vollständigen URLs protokollieren. Auch ein späterer
Reverse Proxy darf solche Angaben nicht ungeprüft in Access- oder Fehlerlogs schreiben.
Timeouts und Größenlimits ersetzen keine vorgelagerte Lastbegrenzung.

### Optionale Android-Anbindung

Ohne `NIKNOLIG_BACKEND_BASE_URL` ist der neue Verbindungstest deaktiviert; die App
bleibt offline nutzbar. Bei einem späteren, freigegebenen Build kann die Variable auf
einen HTTPS-Ursprung ohne Port, Pfad, Zugangsdaten, Query oder Fragment gesetzt werden.
Die App hängt `/v1/health` selbst an. Ungültige Konfiguration bricht den Build ab.
Es gibt kein vorgegebenes Ziel und keine produktive Serveradresse.

Nur ein ausdrücklicher Tastendruck im Elektro-Engel-Bildschirm startet einen
begrenzten Health-GET im Hintergrund. Keine Anfragen beim Start, keine Telemetrie,
keine Übermittlung des Eingabefeldes oder Lernprofils. Ein erfolgreicher Test bestätigt
nur eine kompatible Sandbox, keine Sicherheitsfreigabe. HTTPS-Zertifikate werden mit
dem Android-Truststore geprüft; Klartextverkehr und Weiterleitungen sind gesperrt.
Ein Netzwerkaufruf offenbart dem gewählten Server technisch die Quell-IP; sie wird
vom Backend nicht protokolliert. Bei Fehlern funktionieren lokale Funktionen weiter.

### Vorbereitung für Hetzner Ubuntu – keine Aktivierung

Compose veröffentlicht **keine Ports** und verwendet ein internes Docker-Netz.
Der Prozess läuft ohne Root, mit schreibgeschütztem Root-Dateisystem, ohne Linux
Capabilities und mit `no-new-privileges`. Nur Datenvolume und temporäres Dateisystem
sind beschreibbar. Die Konfiguration ist absichtlich kein produktives
Internet-Deployment; insbesondere ist der Standardbibliothek-HTTP-Server nicht für
direkten öffentlichen Zugriff vorgesehen.

Spätere Schritte, ausschließlich nach Betreiberfreigabe:

1. Vorhandenen Serverzustand, laufende Dienste, freien Speicher und Docker/Compose
   prüfen. Keine Änderungen an vorhandenen Ports, Firewall, Defense oder anderen
   Stacks. Ein separates Arbeitsverzeichnis und einen separaten Compose-Projektnamen
   verwenden; keine gemeinsam genutzten Datenvolumes oder `.env`-Dateien.
2. Den geprüften Commit bereitstellen, `backend/.env.example` als Vorlage verwenden
   und die isolierte Konfiguration mit `docker compose config --quiet` prüfen.
   Standard-Compose benötigt keine Authdatei und lässt Profilrouten gesperrt.
   Nur für synthetische Zugriffstests zusätzlich `compose.auth.yaml` einbinden:
   `docker compose -f compose.yaml -f compose.auth.yaml config --quiet`.
   Dabei verweist `SANDBOX_AUTH_CONFIG_FILE` auf die externe Digestdatei; der
   Container liest sie über `AUTH_CONFIG_FILE` als read-only Secret. Sie muss für
   die Container-UID 10001 lesbar sein, ohne sie allgemein lesbar zu machen.
   Dateien mit Zugangskonfiguration außerhalb der Versionsverwaltung ablegen,
   restriktive Rechte setzen und nur als read-only Secret einbinden. Keine
   Zugangsdaten über Shell-History, Logs, Tickets oder dieses Repository teilen.
3. Image prüfen und bauen. Vor dem ersten Start die Migrationen und vor jedem
   Upgrade deren Kompatibilität prüfen. Die Datenbank wird beim Start migriert.
   Ein bestehendes Volume vorher im gestoppten Zustand sichern; SQLite-Datenbank und
   gegebenenfalls WAL/SHM müssen konsistent bleiben. Sicherung und Wiederherstellung
   zuerst ausschließlich mit synthetischen Daten testen.
4. Erst nach Freigabe den isolierten Stack starten. Healthprüfung innerhalb des
   Containers ausführen; keinen Host-Port ergänzen. Für eine spätere externe
   Verbindung einen separat freigegebenen TLS-Reverse-Proxy und dessen
   Netzwerkanbindung planen: gültiges Zertifikat, explizite Routenauswahl,
   Requestgrößenlimits, Timeouts, Rate-Limits und datensparsame Logs.
   Das interne Netz allein stellt keine Proxy-Anbindung bereit.
5. Vor Veröffentlichung Produktionsauthentifizierung und Datenschutzkonzept
   freigeben lassen. Erst danach Android-Zieladresse setzen und mit Testdaten prüfen.
   Kein automatisches Release, DNS-Umschalten oder Öffnen von Ports durch diese Änderung.
6. Bei Fehlern den isolierten Stack stoppen, vorheriges Image und passende
   konsistente Datenbanksicherung wiederherstellen. Keine automatischen
   Down-Migrationen, kein `docker compose down -v` und keine Volume-Löschung.

Es wurden weder Hetzner-Zugangsdaten verwendet noch produktive Dienste gestartet.
Serveradresse, SSH-Zugang, TLS-/DNS-Freigaben und die Entscheidung zur produktiven
Authentifizierung bleiben außerhalb dieser Implementierung.

### Prüfungen

Backend: im Verzeichnis `backend/` mit `python3 -m unittest discover -s tests -v`.
Die Tests verwenden temporäre Datenbanken, synthetische Daten und kurzlebige
Loopback-Ports, keine externen Dienste. Konfiguration prüfen:
`docker compose config --quiet`; Image ohne Dienststart bauen:
`docker build --tag niknolig-backend:test .`.
Der [Backend-Workflow](https://github.com/nikolausnold/NIKNOLIG/actions/workflows/backend-check.yml)
führt diese Prüfungen aus, ohne Container zu starten oder Deployments auszulösen.

Android: im Repository-Verzeichnis
`./gradlew :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon`.
Die Tests prüfen insbesondere HTTPS-Zielvalidierung und den versionierten Pfad.
Ein echter HTTPS-Verbindungstest auf einem Gerät und ein Deployment-Test am
Hetzner-Server benötigen eine separate Freigabe und sind nicht Teil des Autobuilds.

Prüfstand dieses Autobuilds: Der lokale Android-Aufruf wurde vor der Kompilierung
blockiert, weil `dl.google.com` nicht auflösbar war und deshalb AGP 8.11.1 nicht
geladen werden konnte. Daher sind APK, neue Unit-Tests und Lint lokal **nicht
bestätigt**; es wurde weder die Pluginversion geändert noch eine Ersatzquelle
eingebaut. Der zuvor erfolgreiche
[Android-Lauf 38862786396](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38862786396)
bestätigt nur den Ausgangsstand, nicht diese Änderungen. Der aktualisierte Workflow
führt Unit-Tests, Lint und APK-Build gemeinsam aus.

Backend-Prüfstand: **41 Tests erfolgreich**, OpenAPI-JSON syntaktisch geprüft,
Standard-Compose ohne Authdatei validiert und das Docker-Image erfolgreich gebaut.
Keine Container gestartet: Secret-Leserechte, Volumeinitialisierung und
Healthprüfung unter den tatsächlichen Container-Laufzeitrestriktionen sind
deshalb noch nicht praktisch bestätigt. Auch ein Image-Build allein ist keine
Produktions- oder Deploymentfreigabe.

Die neuen CI-Läufe für
[Android](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38865404923) und
[Backend](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38865405010)
stehen auf `action_required`; es wurden dort noch keine Jobs ausgeführt.
Eine erforderliche GitHub-Freigabe muss der Betreiber selbst vornehmen.
Die Secret-Prüfung fand keine Secrets. CodeQL meldete für Python und Actions
keine Alerts und verwendete bei unverändertem Prüfstand vorhandene Ergebnisse.
Der integrierte automatische Review konnte wegen eines nicht verfügbaren Modells
nicht ausgeführt werden; ein separater lesender Review dient als zusätzliche Prüfung.
Dieser meldete auch nach der abschließenden Migrations-/Packaging-Prüfung keine
wesentlichen Fehler. Das ersetzt nicht die ausstehenden Android- und Laufzeittests.

## Geplante Grenzen

Die App startet ohne Bluetooth-Berechtigungen oder Gerätezugriffe. Der
Implementierungsplan sieht vor, Sicherheitszustände, Berechtigungen und
Gerätevertrauen erst mit expliziten Zustandsübergängen, Tests und nachvollziehbarer
Freigabe einzuführen. KI-gestützte Experimente dürfen keine produktiven
Sicherheitsregeln automatisch verändern.
