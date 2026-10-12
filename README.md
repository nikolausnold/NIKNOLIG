# NIKNOLIG

Eigenständiges Android-Entwicklungsgerüst für NIKNOLIG und Elektro-Engel. Der
aktuelle Bildschirm ist eine Vorschau mit einfacher BLE-Gerätesuche; Verbindung,
Gerätevertrauen, Sicherheitsfreigaben und produktive Gerätefunktionen sind noch
nicht implementiert.

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

Der [Android-Build-Workflow](https://github.com/nikolausnold/NIKNOLIG/actions/workflows/android-build.yml)
erstellt und prüft die Debug-APK. Nach einem erfolgreichen Lauf findest du das
Artifact `niknolig-debug-<run_id>-<run_attempt>` unter **Artifacts** auf der
Workflow-Run-Seite. Es enthält
`app/build/outputs/apk/debug/app-debug.apk` und ist 14 Tage verfügbar.

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
oder eine gültige JKS/PKCS12-Datei mit passendem Alias bricht der Release-Workflow
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
  -keystore "$HOME/.android/keystores/niknolig-upload.jks" \
  -storetype JKS -alias niknolig-upload \
  -keyalg RSA -keysize 2048 -validity 10000
```

Lege eine verschlüsselte Offline-Sicherung des Keystores an. Erzeuge für das
Secret eine geschützte Base64-Datei außerhalb des Repositories und entferne sie
nach dem Übertragen in GitHub:

```sh
umask 077
base64 < "$HOME/.android/keystores/niknolig-upload.jks" \
  | tr -d '\n' \
  > "$HOME/.android/keystores/niknolig-upload.jks.b64"
chmod 600 "$HOME/.android/keystores/niknolig-upload.jks.b64"
```

Richte in der obigen `android-release`-Umgebung unter **Environment secrets**
`ANDROID_KEYSTORE_BASE64` mit dem Inhalt der `.b64`-Datei,
`ANDROID_KEYSTORE_PASSWORD` mit dem Keystore-Passwort, `ANDROID_KEY_ALIAS` mit
`niknolig-upload` und `ANDROID_KEY_PASSWORD` mit dem Passwort des Schlüssels ein.
Übertrage die Werte nur direkt in GitHub und poste sie nirgendwo sonst. Der
Workflow prüft Keystore, Passwort und Alias, bevor Gradle läuft. Erzeuge das
öffentliche Upload-Zertifikat für Play App Signing mit:

```sh
keytool -export -rfc \
  -keystore "$HOME/.android/keystores/niknolig-upload.jks" \
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

Vor einer öffentlichen Veröffentlichung müssen die rechtlich erforderlichen
Betreiber- und Datenschutzangaben ergänzt und geprüft werden. Diese Angaben wurden
nicht erfunden oder als fertige Rechtsseiten ausgegeben.

## Entwicklungs- und Freigabestatus

Die Android-App ist ein einzelner Activity-Prototyp mit ausdrücklich gestarteter,
auf den Vordergrund beschränkter BLE-Gerätesuche. Die Suche zeigt ausgesendete
Gerätenamen und Signalstärken nur vorübergehend an und endet beim Verlassen der
Ansicht; Ergebnisse bleiben nur während einer laufenden Suche sichtbar und werden
nicht dauerhaft gespeichert. Es gibt keine Geräteverbindung, keinen GATT-Zugriff,
kein Lesen oder Schreiben von Gerätewerten, keine Authentifizierung, Autorisierung oder
Gerätevertrauens- und Freigabeentscheidung. Auf Android 12 und höher werden
BLE-Scan- und Adapterberechtigungen angefordert. Die Adapterberechtigung ist für
den Zugriff auf den Bluetooth-Adapter nötig und wird nicht für eine
Geräteverbindung verwendet; auf älteren unterstützten Android-Versionen wird für
die Suche die Standortberechtigung benötigt. Jede Suche endet spätestens
nach 30 Sekunden, beim manuellen Stoppen oder beim Verlassen der Activity.

Im Repository sind derzeit keine Unit- oder Instrumentierungstests vorhanden.
Der Android-Workflow baut ein Debug-APK und prüft dessen Integrität; das ersetzt
weder Funktionstests noch eine SecurityChain-Freigabe. Zustandsübergänge, Berechtigungen und Fehlerzustände der Gerätesuche sind noch nicht
durch automatisierte Funktionstests abgedeckt. Vertrauen und Freigaben müssen vor
jeder Verbindung explizit entworfen und getestet werden. KI-gestützte Experimente dürfen keine
produktiven Sicherheitsregeln automatisch verändern.

Das Projekt ist damit noch nicht releasefähig. Es gibt keine verifizierte
Produktionsfreigabe oder SecurityChain-P0-Testabdeckung.
