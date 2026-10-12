# NIKNOLIG

Eigenständiges Android-Entwicklungsgerüst für NIKNOLIG und Elektro-Engel. Die App
kann nach ausdrücklicher Laufzeitfreigabe für eine begrenzte Zeit BLE-Anzeigen
erfassen und wiederholte Ergebnisse zusammenführen. Ein Scan-Ergebnis ist weder
ein Vertrauensnachweis noch ein Kompatibilitätsnachweis; die erste Umsetzung
stellt keine Geräteverbindung her.

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

Der Build wurde in der aktuellen Umgebung versucht. Gradle konnte das Android
Gradle Plugin nicht aus Google Maven auflösen; die Google-Maven-Domain war über
DNS nicht erreichbar. Daher ist ein erfolgreicher Android-Build noch nicht
nachgewiesen.

## Sicherheitsgrenzen

Die App startet ohne Bluetooth-Berechtigungen oder Gerätezugriffe und fragt die
für BLE-Scans erforderliche Berechtigung erst beim Start der Suche an. Scans
enden nach zehn Sekunden, bei App-Unterbrechung oder auf ausdrücklichen Stopp.
Es gibt noch keinen Classic-Bluetooth-Pfad, GATT-Untersuchung,
Connection-Manager, Gerätevertrauen, Geräteadapter oder Verbindungstest.
KI-gestützte Experimente dürfen keine produktiven Sicherheitsregeln automatisch
verändern.

Die Android-CI hat den Debug-Build auf Commit
`e291d4d7d0d2098b7ee5ab2ab1156facb1e4ad01` erfolgreich erstellt. Lokale Builds
und Unit-Tests der BLE-Änderungen konnten in der aktuellen Umgebung wegen des
nicht auflösbaren Android Gradle Plugins nicht ausgeführt werden.
