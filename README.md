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

Ein früherer lokaler Buildversuch war durch nicht erreichbares Google Maven
blockiert. Unabhängig davon waren die vorhandenen Android-CI-Läufe
[Build #10, Versuch 2](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38206899378)
und [Build #12](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38209774402)
erfolgreich. Nur für #10 wurde dabei das
[Debug-APK-Artefakt](https://github.com/nikolausnold/NIKNOLIG/actions/runs/38206899378/artifacts/11727961800)
nachgewiesen. Diese Ergebnisse betreffen den damaligen Stand, nicht spätere
Änderungen; Artefakte können ablaufen.

## Prüfungen

Der Workflow prüft Android-Lint und kompiliert App sowie Instrumentierungstests
in einem Gradle-Aufruf:

```sh
./gradlew :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

Das Kompilieren der Test-APK ist kein bestandener Laufzeittest. Mit einem
verfügbaren Testgerät oder Emulator lassen sich die Tests ausführen:

```sh
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Die Tests prüfen Systemleisten-/Cutout-Abstände, wiederholte Insets,
Activity-Neustart, den Vorschauhinweis und das Fehlen angeforderter
Geräteberechtigungen. Für beide Insets-Zweige sind Android API 26–29 und
API 30 oder neuer erforderlich; auf der jeweils anderen Version wird der
betreffende Test übersprungen. CI startet derzeit keinen Emulator und führt
diese Laufzeittests nicht aus. BLE, SecurityChain, arabisches RTL und weitere
Fachfunktionen sind weiterhin nicht nachgewiesen.

## Geplante Grenzen

Die App startet ohne Bluetooth-Berechtigungen oder Gerätezugriffe. Der
Implementierungsplan sieht vor, Sicherheitszustände, Berechtigungen und
Gerätevertrauen erst mit expliziten Zustandsübergängen, Tests und nachvollziehbarer
Freigabe einzuführen. KI-gestützte Experimente dürfen keine produktiven
Sicherheitsregeln automatisch verändern.
