# Appothek – Design

Stand: 2026-11-09 · Status: Entwurf zur Freigabe

## Worum es geht und warum

Nicht jede neue App-Idee soll als eigene App in den Play Store. Stattdessen gibt es eine Sammel-App, in der jede
Idee ein eigenes Tool ist. Der Name **Appothek** ist ein Wortspiel aus App, Mediathek und Apotheke: ein Regal voller
kleiner Helfer, die man im Alltag immer mal braucht.

Zum Start enthält die App zwei Tools:

- **Buchstabierer**: heute eine eigene App im Play Store (privates Repo `nexus421/Buchstabierer`, lokal unter
  `~/AndroidStudioProjects/Buchstabierer`)
- **InkTap**: dieses Repo, heute eine eigenständige App, die per GitHub-Release als APK verteilt wird

Vorbild für die Tool-Struktur ist MultiDevTools (`nexus421/MultiDevTools`, Desktop-App). Dort ist jedes Tool ein
`WindowElement` mit Name, Oberfläche und Start/Ende-Hooks und wird in einer Liste angemeldet.

## Ziele

- Ein neues Tool kostet möglichst wenig: ein Modul anlegen und das Tool an einer Stelle anmelden.
- Die Appothek ersetzt den Buchstabierer im Play Store. Bestehende Nutzer bekommen sie als Update, ohne Daten zu
  verlieren.
- Jedes Tool kann auf Wunsch ein eigenes Symbol im Launcher haben und verhält sich dann wie eine eigene App.
- Bei jedem GitHub-Release entstehen automatisch eine signierte APK und ein AAB.
- Das Repo ist öffentlich (Open Source).

## Nicht-Ziele

- Automatischer Upload in den Play Store. Das AAB lädt der Entwickler von Hand hoch, eine Automatisierung kommt
  vielleicht später.
- Nachladen von Tools bei Bedarf (Play Feature Delivery). Das funktioniert nur über Play, nicht mit der GitHub-APK.
- Unterstützung für Android unter 15.
- Ein Update-Pfad für bestehende InkTap-Installationen. Die App-ID ändert sich, und das ist in Ordnung, weil InkTap
  bisher praktisch niemand nutzt.

## Anti-Ziele (wäre ein Fehlschlag, selbst wenn es technisch läuft)

- Buchstabierer-Nutzer verlieren nach dem Update Verlauf, eigenes Alphabet, Favoriten oder Einstellungen.
- Der private Signierschlüssel landet im öffentlichen Repo, auch nicht in der Git-History.
- Tools hängen unbemerkt voneinander ab, sodass sich keins mehr allein umbauen oder entfernen lässt.
- Die App braucht Internet oder trackt Nutzer.
- Die App ist auf Handys ohne NFC im Play Store unsichtbar oder stürzt dort ab.
- Mit jedem Update erscheint ungefragt ein neues Symbol im App-Drawer.

## Feste Rahmenbedingungen

| Punkt | Festlegung |
|---|---|
| App-ID | `bayern.kickner.buchstabierer` (für immer, sie identifiziert die Play-Store-App) |
| Signatur | Der bestehende Buchstabierer-Key (`keystore/buchstabierer.jks` im privaten Repo) |
| minSdk | 35 (Android 15). Buchstabierer hatte bisher 26 |
| compileSdk / targetSdk | 37, wie heute in beiden Repos |
| versionCode | Muss größer als 11 sein (letzter Buchstabierer-Stand). Das InkTap-Schema `major*10000+minor*100+patch` erfüllt das |
| Internet | Keine `INTERNET`-Berechtigung, kein Tracking, in keinem Tool. Gilt als Grundsatz für alle künftigen Tools |
| NFC | `android.hardware.nfc` mit `required="false"`. Die App muss ohne NFC voll funktionieren, nur InkTap ist dann nicht nutzbar |
| Repo | Dieses Repo (`nexus421/InkTap`) wird zur Appothek umgebaut und auf GitHub in „Appothek“ umbenannt |
| Buchstabierer-Code | Wird **kopiert**, nie gemergt. Die Git-History des privaten Repos enthält den Keystore, die `.pepk`-Datei und einen Passwort-Hinweis in `app/build.gradle.kts`. Der Hinweis wird beim Kopieren nicht übernommen |
| Altes Repo | Das private Buchstabierer-Repo darf gelöscht werden, sobald der Key sicher in GitHub Secrets und offline gesichert ist |
| Lizenz | Apache 2.0. Das Repo bekommt eine `LICENSE`-Datei (bisher fehlt sie), und „Über die Appothek“ listet die Lizenzen aller Abhängigkeiten und der Wortliste |
| Wortlisten | Die bisherigen `dic_0.dic` bis `dic_4.dic` (ca. 26 MB, Herkunft unbekannt) werden **nicht** übernommen, sondern ersetzt, siehe unten |

## Ansätze für die Code-Struktur

| Ansatz | Kurz | Bewertung |
|---|---|---|
| A · Ein Modul, ein Paket pro Tool | Am einfachsten, ein neues Tool ist ein Ordner und eine Zeile | Nichts verhindert, dass Tools sich gegenseitig benutzen. Wird mit vielen Tools unübersichtlich |
| **B · Ein Gradle-Modul pro Tool** | `:app`, `:core`, ein Modul pro Tool, gemeinsame Gradle-Logik in `build-logic` | **Gewählt.** Die Trennung ist erzwungen, Builds sind inkrementell. Einmal etwas mehr Gradle-Aufwand |
| C · Dynamic Feature Modules | Tools werden bei Bedarf über Play nachgeladen | Verworfen, weil es mit der GitHub-APK nicht funktioniert |

Begründung für B: Die App soll über Jahre wachsen, und eine spätere Umstellung von A nach B passiert in der Praxis
nie, weil sie mit jedem Tool teurer wird. Jetzt, mit zwei Tools, die ohnehin portiert werden, ist sie am
billigsten.

## Struktur

```
app/                      Startseite, Navigation, Einstiegspunkte, Launcher-Einträge, Manifest
core/                     Tool-Schnittstelle, Theme, gemeinsame UI-Bausteine, gemeinsame Helfer
tools/buchstabierer/      Buchstabierer
tools/inktap/             InkTap (inkl. des heutigen Android-freien core-Pakets und seiner JVM-Tests)
build-logic/              Convention-Plugins, z. B. "appothek.tool" für Tool-Module
```

Abhängigkeitsregeln:

- `app` kennt `core` und alle Tools.
- Tools kennen nur `core` und bei Bedarf Module unter `shared/`.
- Tools kennen sich **nicht** gegenseitig.
- Wird Code geteilt, zieht er bewusst nach `core` (braucht fast jedes Tool) oder in ein eigenes Modul unter
  `shared/` (brauchen nur einige Tools, z. B. später `shared/qrcode`, falls ein zweites Tool QR-Codes erzeugt).

### Tool-Schnittstelle (in `core`)

Sinngemäß, die genaue Form legt der Builder fest:

```kotlin
interface Tool {
    val id: String                    // stabil, z. B. "buchstabierer"; für Einstellungen, Shortcuts, Launcher-Einträge
    val name: String                  // bzw. String-Ressource
    val description: String           // eine Zeile, z. B. "Anton, Berta, Cäsar…"
    val colors: ToolColors            // Verlauf hell + dunkel
    val motif: ImageVector            // großes Motiv im Kartenhintergrund
    fun availability(context: Context): ToolAvailability   // Available | Unavailable(Grund); „NFC aus“ behandelt das Tool selbst
    @Composable fun Content()
}
```

Die Liste aller Tools steht an einer Stelle in `app`. Startseite, Einstellungen, Launcher-Einträge und App-Shortcuts
leiten sich aus dieser Liste ab, soweit das technisch geht. Die Launcher-Einträge müssen im Manifest stehen, siehe
unten.

## Oberfläche

### Startseite (vorläufig)

- Oben der Titel „Appothek“, rechts ein Zahnrad für die Einstellungen.
- Darunter ein **festes Raster mit immer zwei Karten nebeneinander**. Alle Karten sind quadratisch und gleich groß,
  die Anordnung ändert sich nie, neue Tools kommen hinten dazu.
- Jede Karte hat einen leichten Verlauf in der Farbe ihres Tools, oben links Name und eine Zeile Beschreibung und
  rechts unten ein großes, blasses, angeschnittenes Motiv.
- Hell- und Dunkelmodus. Im Dunkelmodus sind die Verläufe tiefer, damit sie nicht leuchten. Standard ist „wie das
  System“.
- Referenz: Variante 1 in `.superpowers/brainstorm/…/content/startseite-v6b.html`. Die Datei ist nicht im Repo, die
  Beschreibung hier ist maßgeblich.

Der Entwickler ist mit der Optik **noch nicht zufrieden**. Sie gilt als vorläufig. Sobald das Gerüst steht, wird die
Startseite in Compose gebaut und am echten Gerät (Pixel 10 Pro) hell und dunkel nachgeschärft. Bei dieser
Gelegenheit entstehen auch das App-Icon und die Motive pro Tool. Die Startseite liegt deshalb isoliert in `app`, und
Änderungen daran dürfen kein Tool berühren.

### Ein Tool öffnen

- **Von der Startseite:** Das Tool öffnet sich im Vollbild innerhalb der Appothek. „Zurück“ führt zur Startseite.
- **Über ein eigenes Launcher-Symbol:** Das Tool verhält sich **wie eine eigene App**. Es öffnet direkt, „Zurück“
  schließt es und führt zum Homescreen, und in der Übersicht der zuletzt geöffneten Apps erscheint es als eigene
  Karte mit eigenem Namen und Symbol, getrennt von der Appothek. Es gibt keinen Pfeil zurück zur Appothek.

Technisch heißt das, pro Tool gibt es in `app` zwei Manifest-Einträge:

1. **Eine echte Activity** (z. B. `BuchstabiererActivity`) mit eigener `taskAffinity`, eigenem Label und Icon,
   immer aktiviert, ohne Launcher-Filter. Sie zeigt nur `tool.Content()`. Shortcuts und angeheftete Symbole zeigen
   auf diese Activity, weil eine deaktivierte Komponente nicht gestartet werden kann.
2. **Ein `activity-alias`** mit `MAIN`/`LAUNCHER`-Filter, der auf diese Activity zeigt. Das ist der ein- und
   ausschaltbare Eintrag im App-Drawer (standardmäßig `android:enabled="false"`).

Geprüft in der [Android-Doku zu `<activity-alias>`](https://developer.android.com/guide/topics/manifest/activity-alias-element):
`taskAffinity` gehört nicht zu den Attributen eines Alias. Attribute außerhalb dieser Teilmenge übernimmt der Alias
von seiner Ziel-Activity. Der Alias kann also keine eigene Affinität setzen, erbt aber die der Tool-Activity, und
genau das ist hier gewollt.

Ein Tool kann gleichzeitig in der Appothek und als eigene App offen sein. Gespeicherte Daten teilen sich beide über
dieselben Dateien (DataStore als Singleton pro Prozess), der Bildschirmzustand ist pro Activity getrennt. InkTap muss
NFC an die gerade sichtbare Activity binden.

### Eigene Launcher-Symbole

- Standardmäßig **ausgeblendet** (`android:enabled="false"` im Manifest). Nach einer Neuinstallation gibt es nur das
  Appothek-Symbol.
- Einschalten per langem Druck auf die Kachel („Als eigenes Symbol anzeigen“) oder im Einstellungs-Sheet.
- Umschalten über `PackageManager.setComponentEnabledSetting(…, DONT_KILL_APP)`.
- Zusätzlich App-Shortcuts (langer Druck auf das Appothek-Symbol zeigt die Tools). Sie werden beim App-Start als
  **dynamische** Shortcuts gesetzt (`ShortcutManagerCompat.setDynamicShortcuts`), nicht statisch in
  `shortcuts.xml`. Statische Shortcuts lassen sich zur Laufzeit nicht ausblenden, und InkTap darf ohne NFC nicht
  erscheinen. Die meisten Launcher zeigen ohnehin nur vier Shortcuts.

### Bestehende Buchstabierer-Nutzer

- Ihr Homescreen-Symbol zeigt auf die Komponente `bayern.kickner.buchstabierer.MainActivity`. Diese Komponente muss
  es weiter geben, sonst entfernt der Launcher das Symbol. Sie wird zum **Haupteinstieg der Appothek**, z. B. als
  `activity-alias` mit genau diesem Namen, der auf die Appothek-Activity zeigt. Damit bleibt der Code-Namespace frei
  wählbar.
- Name und Bild des Symbols wechseln dadurch zu „Appothek“, ein Tippen öffnet die Startseite.
- Beim **ersten Start nach dem Update** erscheint einmalig ein Hinweis: „Der Buchstabierer wohnt jetzt in der
  Appothek. Soll er trotzdem ein eigenes Symbol auf dem Startbildschirm bekommen?“ mit Ja und Nein. Ja schaltet den
  Launcher-Eintrag des Buchstabierers ein und öffnet zusätzlich den Systemdialog zum Anheften eines Symbols
  (`ShortcutManager.requestPinShortcut`), das auf den Einstiegspunkt des Buchstabierers zeigt. Das alte Symbol
  selbst ist danach immer die Appothek. Ein bloßes „behalten“ wäre technisch nicht möglich.
- Ein Umsteiger wird daran erkannt, dass die alten Buchstabierer-Dateien existieren (siehe Datenkompatibilität). Ob
  der Hinweis schon gezeigt wurde, speichert die Appothek in ihren eigenen Einstellungen.

### Tool nicht verfügbar

- **Kein NFC im Gerät:** Die InkTap-Kachel ist ausgegraut und zeigt „Dein Handy hat kein NFC“. Ein eigenes Symbol
  lässt sich nicht einschalten.
- **NFC ausgeschaltet:** Das Tool öffnet sich und zeigt einen Hinweis mit einem Knopf, der direkt zu den
  NFC-Einstellungen führt.
- Das gilt allgemein über `availability()`, nicht als Sonderfall für InkTap.

### Einstellungen

- Das Zahnrad öffnet ein **Modal Bottom Sheet** (Material 3). Die Startseite bleibt dahinter sichtbar, Wischen nach
  unten oder „Zurück“ schließt es.
- Inhalt:
  - Design: System, Hell oder Dunkel (Segmented Button)
  - Eigene Symbole: ein Schalter pro Tool, ausgegraut, wenn das Tool nicht verfügbar ist
  - „Über die Appothek“: öffnet eine eigene Seite mit Version und Open-Source-Lizenzen
- Tool-eigene Einstellungen bleiben im jeweiligen Tool.

## Datenkompatibilität Buchstabierer

Der Buchstabierer speichert heute in zwei Dateien. Beide müssen nach dem Update genau so gelesen werden:

| Datei | Inhalt | Schlüssel |
|---|---|---|
| DataStore `settings` (`preferencesDataStore(name = "settings")`) | Tafel, Name, Debug, Favoriten-Reihen, Verlauf, eigenes Alphabet | `selected_type`, `name`, `show_debug`, `favorite_buttons_row_count`, `history` (mit `|` getrennt), `custom_alphabet` (`A:Wort|B:Wort`) |
| SharedPreferences `settings` | Texte der Favoriten-Knöpfe, alte Einstellungen, Migrationsmarker | `"<spalte> <zeile>"`, `type`, `name`, `debug`, `favoriteButtonsRowCount`, `migrated_to_datastore` |

Regeln:

1. Das Buchstabierer-Modul liest und schreibt beide Dateien mit denselben Namen, Schlüsseln und Formaten. Es wird
   nichts umbenannt oder migriert.
2. Der Dateiname `settings` gehört allein dem Buchstabierer. Die Appothek nutzt `appothek`, InkTap weiterhin
   `inktap`. Zwei DataStores auf derselben Datei im selben Prozess führen zum Absturz. Das wird im Code dokumentiert.
3. Die Enum-Namen der Tafeln (`DIN5006`, `DIN5006_2022`, `RANDOM_WORD`, `ROBERT_LOGGIA`, `INTERNATIONAL`, `CUSTOM`)
   bleiben, weil sie als Text gespeichert sind.
4. Die bestehende Migration von SharedPreferences nach DataStore (`Settings.init`) bleibt erhalten, für Nutzer, die
   von einer sehr alten Version kommen.
5. **Pflichttest Update-Pfad**, zweistufig:
   - **Während der Umsetzung (Emulator, Debug-Builds):** Den alten Buchstabierer als Debug-Build installieren
     (`bayern.kickner.buchstabierer.debug`, Debug-Key), Verlauf, eigenes Alphabet, Favoriten und eine andere Tafel
     einstellen, dann den Appothek-Debug-Build mit gleicher App-ID und gleichem Debug-Key per `adb install -r`
     darüber installieren und prüfen, dass alles noch da ist und das alte Launcher-Symbol weiter funktioniert.
   - **Vor dem ersten Release (Entwickler, Release-Key):** Dasselbe einmal mit der Play-Version auf einem echten
     Gerät und der signierten Release-APK.

## Wortliste für Zufallswörter

Die Zufallswörter des Buchstabierers lesen heute fünf Dateien `res/raw/dic_0.dic` bis `dic_4.dic`
(1,6 Mio. Wörter, ca. 26 MB). Woher sie stammen, ist nicht mehr bekannt. Damit im öffentlichen Repo nichts mit
unklarer Lizenz landet, werden sie ersetzt:

- **Quelle:** `german.dic` aus dem [Free German Dictionary](https://sourceforge.net/projects/germandict/), laut
  Projektseite Public Domain. Beim Herunterladen wird die Lizenzangabe im Archiv selbst geprüft und mit Version bzw.
  Datum im Repo festgehalten. Die Quelle wird unter „Über die Appothek“ und im README genannt, auch wenn Public
  Domain das nicht verlangt.
- **Gekürzt per Skript im Repo** (`tools/buchstabierer/wordlist/`), damit sich die Liste jederzeit
  reproduzierbar neu erzeugen lässt. Ins Repo und in die App kommt nur das Ergebnis. Die genauen Filter stehen in
  `tools/buchstabierer/wordlist/SOURCE.md`: nur Substantive aus Buchstaben, 4 bis 10 Buchstaben, keine gebeugten
  Formen, keine Genitive auf „-ens“, eine Sperrliste für beleidigende, vulgäre und anstößige Wörter.
- **Ergebnis:** 135.677 Wörter, 1,36 MB, eine einzige Datei. Die Liste wird einmal pro Prozess geladen.
- Die Option „Zufallswort“ bleibt in den Einstellungen erhalten (gespeicherter Wert `RANDOM_WORD`).

Backups: Die Daten des Buchstabierers werden wie bisher von Android gesichert (`allowBackup`). Die Eingaben von
InkTap bleiben wie bisher ausgeschlossen. Die Backup-Regeln aus InkTap werden entsprechend erweitert.

## Release und Signierung

- Der Workflow `.github/workflows/release.yml` bleibt in seiner Form: Ein veröffentlichtes GitHub-Release mit Tag
  `vX.Y.Z` baut, testet und hängt Artefakte an.
- Neu: Zusätzlich zu `assembleRelease` läuft `bundleRelease`. Angehängt werden `Appothek-X.Y.Z.apk` und
  `Appothek-X.Y.Z.aab`.
- Secrets werden umbenannt auf `APPOTHEK_KEYSTORE_BASE64`, `APPOTHEK_KEYSTORE_PASSWORD`, `APPOTHEK_KEY_ALIAS` und
  `APPOTHEK_KEY_PASSWORD`. Der Inhalt ist der Buchstabierer-Key. Die alten `INKTAP_*`-Secrets werden danach
  gelöscht.
- Absicherung:
  - Die Secrets liegen in einem GitHub-Environment `release`, das nur Tags `v*` nutzen dürfen, optional mit
    Freigabe durch den Entwickler.
  - Alle Actions werden auf Commit-SHAs gepinnt statt auf `@v5`.
  - Nur der Entwickler hat Schreibrechte am Repo.
  - Der Keystore wird nach dem Build gelöscht, wie heute schon.
- Der Build-Workflow (`build.yml`) baut alle Module und führt alle Tests aus.
- **Der Key ist der App-Signaturschlüssel.** Die App wurde ursprünglich als APK veröffentlicht, die mit diesem Key
  signiert war, und die `.pepk`-Datei ist der Export an Google Play App Signing. GitHub-APK und Play-Version sind
  damit gegenseitig updatebar.
- **Kurze Prüfung vor dem ersten Release (Entwickler):** Den SHA-256-Fingerabdruck
  (`keytool -list -v -keystore buchstabierer.jks`) mit dem „App-Signaturschlüssel-Zertifikat“ in der Play Console
  vergleichen. Das ist eine Absicherung, keine offene Frage. Stimmt er wider Erwarten nicht, wird vor dem Release
  neu entschieden.

## Play Store

- Dieselbe App, neuer Auftritt: Titel „Appothek“ (maximal 30 Zeichen, ggf. mit Untertitel), neues Icon, neue
  Beschreibung und neue Screenshots. In der Beschreibung wird der Buchstabierer ausdrücklich erwähnt.
- Bewertungen und Installationszahlen bleiben erhalten.
- Datensicherheit und Altersfreigabe prüfen: NFC und die Bildauswahl von InkTap kommen neu dazu, eine
  Internetberechtigung gibt es weiterhin nicht.
- Beim ersten Appothek-Release das alte Buchstabierer-App-Bundle in der Play Console **beibehalten** (Option
  „vorheriges App-Bundle beibehalten“ im Release). Dann bekommen Geräte unter Android 15 weiterhin den alten
  Buchstabierer. Die Option existiert, wie genau sie bei unterschiedlichem minSdk greift, war online nicht offiziell
  belegbar. Das wird beim Anlegen des Releases in der Play Console geprüft.

## Dem Builder überlassen

- Genaue Form der Tool-Schnittstelle, Navigation (z. B. Navigation Compose) und Zustandsverwaltung.
- Aufbau von `build-logic` und Version Catalog (Basis ist der Catalog von InkTap). Die Bibliotheken beider Apps
  werden vereinheitlicht. Geprüft: Der Buchstabierer bindet `KotNexLib` 3.2.1 zwar ein, importiert aber nichts
  daraus. Die Abhängigkeit fällt deshalb weg. `ComposeLoadingDialog` (eine Stelle, Ladeanzeige im Stil „KITT“) gibt
  es als 3.0.0 auch auf `maven.kickner.bayern`. JitPack wird damit nicht mehr gebraucht.
- Wie viel vom Buchstabierer-Code beim Port modernisiert wird (z. B. LiveData raus), solange Verhalten und
  gespeicherte Daten gleich bleiben.
- Paketnamen: Der Code-Namespace darf von der App-ID abweichen, z. B. `bayern.kickner.appothek.*`.

## Entscheidungen von Claude, die der Entwickler prüfen sollte

- Startversion `1.0.0` (versionCode 10000 > 11). Die Appothek ist ein neues Produkt, deshalb wird die Nummerierung
  des Buchstabierers (7.0) nicht fortgesetzt.
- „Ja“ im Umsteiger-Hinweis legt per Systemdialog ein Buchstabierer-Symbol auf den Startbildschirm.
- Die Filter für die Wortliste: 4 bis 12 Buchstaben, gebeugte Formen raus, Sperrliste, unter 200.000 Wörter.
- Die App-Shortcuts (langer Druck auf das Appothek-Symbol) öffnen ein Tool wie eine eigene App, also genauso wie
  sein eigenes Launcher-Symbol.
- Die Design-Einstellung (System, Hell, Dunkel) gilt auch innerhalb der Tools. Dynamische Farben (Material You)
  gibt es nicht, weil jedes Tool seine feste Farbe hat.
- Die App-Sprache ist Deutsch. Die Appothek-Oberfläche bekommt wie InkTap (hat schon `values-en`) zusätzlich Englisch, der Buchstabierer bleibt rein deutsch, weil er deutsche Buchstabiertafeln erklärt.
