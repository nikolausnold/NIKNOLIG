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
Artifact `niknolig-debug-apk-<run_id>-<run_attempt>` unter **Artifacts** auf der
Workflow-Run-Seite. Es enthält
`app/build/outputs/apk/debug/app-debug.apk` und ist 14 Tage verfügbar.

## Android-Release-Build

Ein signiertes Android App Bundle wird beim Pushen eines Tags im Format
`vMAJOR.MINOR.PATCH` (zum Beispiel `v1.0.0`) gebaut und als Workflow-Artefakt
bereitgestellt. Die Tag-Version wird als App-Version übernommen; eine aufsteigende
Buildnummer wird als Android-`versionCode` gesetzt. Dafür müssen in den
[Repository-Secrets](https://github.com/nikolausnold/NIKNOLIG/settings/secrets/actions)
vor dem Taggen diese Werte eingerichtet sein:

- `ANDROID_KEYSTORE_BASE64`: Base64-kodierter Upload-Keystore
- `ANDROID_KEYSTORE_PASSWORD`: Keystore-Passwort
- `ANDROID_KEY_ALIAS`: Alias des Upload-Schlüssels
- `ANDROID_KEY_PASSWORD`: Passwort des Upload-Schlüssels

Der Release-Workflow prüft die Signatur des AAB. Der Keystore wird nur während
des CI-Laufs in einem temporären Runner-Verzeichnis abgelegt; Schlüssel und
Passwörter gehören weder ins Repository noch in Build-Logs. Ohne diese Secrets
bricht der Release-Workflow ab. Ein lokaler `bundleRelease`-Build ohne
Signierungsvariablen ist nicht für einen Store-Upload verwendbar.

Ein erfolgreich gebautes AAB ist noch keine Freigabe zur Veröffentlichung. Vor
einem Store-Release sind unter anderem Datenschutz- und Betreiberangaben,
Store-Eintrag und Daten-Sicherheitsdeklaration sowie Tests auf unterstützten
Geräten und eine fachliche Sicherheitsfreigabe erforderlich. Die App bietet
derzeit nur eine einfache BLE-Suche, keine produktive Geräteverbindung oder
Gerätesteuerung.

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
veröffentlicht bei Änderungen an der Website auf `main` nur `index.html` und
`styles.css`, `robots.txt`, `sitemap.xml` und die Website-Assets; ein manueller
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
Gerätevertrauens- und Freigabeentscheidung. Auf Android 12 und höher wird nur die
Berechtigung für BLE-Scans angefordert; auf älteren unterstützten Android-Versionen
wird für die Suche die Standortberechtigung benötigt.

Im Repository sind derzeit keine Unit- oder Instrumentierungstests vorhanden.
Der Android-Workflow baut ein Debug-APK und prüft dessen Integrität; das ersetzt
weder Funktionstests noch eine SecurityChain-Freigabe. Zustandsübergänge, Berechtigungen und Fehlerzustände der Gerätesuche sind noch nicht
durch automatisierte Funktionstests abgedeckt. Vertrauen und Freigaben müssen vor
jeder Verbindung explizit entworfen und getestet werden. KI-gestützte Experimente dürfen keine
produktiven Sicherheitsregeln automatisch verändern.

Das Projekt ist damit noch nicht releasefähig. Es gibt keine verifizierte
Produktionsfreigabe oder SecurityChain-P0-Testabdeckung.
