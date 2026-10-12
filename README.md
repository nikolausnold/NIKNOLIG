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

Der Debug-Build wurde am 12.10.2026 in
[GitHub Actions erfolgreich ausgeführt](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38223083688).
Das Artefakt dieses Laufs belegt den APK-Build, nicht die Funktionsfähigkeit von
BLE oder Sicherheitsfreigaben.

## Gerätestand vom 12.10.2026

Beim realen Android-Test wurde folgender Zustand gemeldet:

- Branding: NIKNOLIG / Elektro-Engel
- Design: dunkler Hintergrund mit goldenen Akzenten
- Systemstatus: „Entwicklungsgerüst“
- Geräte: „Keine Geräte verbunden“
- Hinweis: „Vorschau – noch keine Geräteverbindung, Sicherheitsfreigabe oder produktive Funktion.“

Der gemeldete Bildschirm entspricht den Strings und dem Layout im Repository.
Der Testbericht enthält kein Gerätemodell und keine Android-Version; der
Gerätetest wurde hier nicht unabhängig wiederholt.

## Implementierungs- und Testergebnis

`MainActivity` lädt derzeit nur das Layout. Im Manifest sind keine
Bluetooth-Berechtigungen eingetragen; im Repository gibt es keine BLE-Scanner-,
GATT- oder Connection-Manager-Implementierung und keine Testquellen. Der
Gerätebildschirm belegt daher keine BLE-Verbindung. Ebenso wurden weder
Sicherheitsfreigaben noch produktive Funktionen getestet.

Nachgewiesen ist der erfolgreiche Debug-APK-Build in GitHub Actions. Ein
automatisierter BLE- oder Gerätetest ist im Repository nicht vorhanden.

## Nächster konkreter Schritt

Vor der BLE-Implementierung das unterstützte Zielgerät (Hersteller und Modell),
dessen BLE-Dienst/Protokoll und die benötigte Android-Version festlegen. Danach
kann der begrenzte Scan- und Verbindungsablauf mit passenden Tests spezifiziert
werden; bis dahin bleibt die App eine Vorschau.

## Geplante Grenzen

Die App startet ohne Bluetooth-Berechtigungen oder Gerätezugriffe. Der
Implementierungsplan sieht vor, Sicherheitszustände, Berechtigungen und
Gerätevertrauen erst mit expliziten Zustandsübergängen, Tests und nachvollziehbarer
Freigabe einzuführen. KI-gestützte Experimente dürfen keine produktiven
Sicherheitsregeln automatisch verändern.
