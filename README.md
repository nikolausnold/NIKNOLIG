# NIKNOLIG

Eigenständiges Android-Projekt für NIKNOLIG und Elektro-Engel. Die App bietet
eine dunkle Oberfläche mit goldenen Akzenten, eine zeitlich begrenzte BLE-Suche,
Anzeigedetails und eine ausdrücklich vom Nutzer gestartete GATT-Verbindung.

## Implementierte BLE-Funktionen

- BLE-Sender suchen und Anzeigenamen, Signalstärke, Service-UUIDs sowie
  Hersteller- und Dienstdaten ansehen.
- Auf ausdrückliche Nutzerauswahl mit einem Sender verbinden und trennen.
- Nach Verbindung und einer weiteren ausdrücklichen Auswahl GATT-Dienste,
  primären/sekundären Typ, Merkmale, deklarierte Eigenschaften und
  Merkmal-/Deskriptor-Berechtigungen anzeigen, kopieren und auf ausdrückliche
  Auswahl über Androids Teilen-Menü weitergeben.

Eine Verbindung oder angezeigte GATT-Eigenschaft verifiziert weder Identität
noch Vertrauenswürdigkeit. Die App liest oder schreibt keine Merkmalswerte und
sendet keine Steuerbefehle. Gerätevertrauen, sichere Freigaben, produktive
Steuerung und ein Master-Funktionskatalog sind in diesem Checkout nicht
implementiert.

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

Bluetooth-Berechtigungen werden erst bei der jeweils vom Nutzer angeforderten
Suche oder Verbindung abgefragt. GATT-Dienste werden nicht automatisch
abgerufen, sondern nur auf ausdrückliche Auswahl während einer aktiven
Verbindung.
