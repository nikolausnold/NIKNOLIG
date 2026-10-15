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
veröffentlicht bei Änderungen an der Website auf `main` nur `index.html` und
`styles.css`; ein manueller Start ist ebenfalls möglich. Aktiviere unter
[Repository-Einstellungen → Pages](https://github.com/nikolausnold/NIKNOLIG/settings/pages)
als Veröffentlichungsquelle **GitHub Actions**. Nach einem erfolgreichen Lauf zeigt
der Workflow die veröffentlichte URL an. Verfügbarkeit und Kosten hängen von
Repository-Sichtbarkeit und GitHub-Tarif ab.

Vor einer öffentlichen Veröffentlichung müssen die rechtlich erforderlichen
Betreiber- und Datenschutzangaben ergänzt und geprüft werden. Diese Angaben wurden
nicht erfunden oder als fertige Rechtsseiten ausgegeben.

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

## Geplante Grenzen

Die App startet ohne Bluetooth-Berechtigungen oder Gerätezugriffe. Der
Implementierungsplan sieht vor, Sicherheitszustände, Berechtigungen und
Gerätevertrauen erst mit expliziten Zustandsübergängen, Tests und nachvollziehbarer
Freigabe einzuführen. KI-gestützte Experimente dürfen keine produktiven
Sicherheitsregeln automatisch verändern.
