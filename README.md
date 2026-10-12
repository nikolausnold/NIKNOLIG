# NIKNOLIG

Eigenständiges Android-Entwicklungsgerüst für NIKNOLIG und Elektro-Engel. Der
aktuelle Bildschirm ist eine Vorschau; BLE, Gerätevertrauen,
Sicherheitsfreigaben und produktive Funktionen sind noch nicht implementiert. Der
Elektro-Engel-Button öffnet einen Hinweis zur Assistenz; eine funktionale
Assistenz ist noch nicht verfügbar.

Die Oberfläche und der Assistenz-Einstieg sind auf Deutsch, Englisch, Arabisch und
Japanisch lokalisiert. Der Button ist rechts unten verankert und berücksichtigt
den verfügbaren Systembereich. Geräteberechtigungen werden nicht angefordert.

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

## Geplante Grenzen

Die App startet ohne Bluetooth-Berechtigungen oder Gerätezugriffe. Der
Implementierungsplan sieht vor, Sicherheitszustände, Berechtigungen und
Gerätevertrauen erst mit expliziten Zustandsübergängen, Tests und nachvollziehbarer
Freigabe einzuführen. KI-gestützte Experimente dürfen keine produktiven
Sicherheitsregeln automatisch verändern.
