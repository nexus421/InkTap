# InkTap: Android-App für das Waveshare 2.13" NFC-E-Paper

> **Für KI-Agenten:** Dieses Dokument ist Spezifikation und Umsetzungsplan in einem. Arbeite die Tasks in
> Abschnitt 9 der Reihe nach ab (empfohlen: `superpowers:subagent-driven-development`, sonst
> `superpowers:executing-plans`). Schritte sind Checkboxen (`- [ ]`). Der Code in **Anhang A und B ist bereits
> kompiliert und getestet**. Übernimm ihn wörtlich, statt ihn neu zu schreiben.

| | |
|---|---|
| **Ziel** | Handy ans Display halten, fertig: QR-Codes, Text, Barcodes und Bilder per NFC auf das passive Waveshare-2.13"-E-Paper schreiben. |
| **Architektur** | Eine Activity, ein Screen (Contract, ViewModel, View nach `ANDROID.md`). Die gesamte Logik (Layout, Codes, Bitpacking, NFC-Protokoll) liegt Android-frei in `core/` und ist mit JUnit getestet. Android liefert nur Text-Rendering, Bild-Dekodierung und den NFC-Transport. |
| **Tech-Stack** | Kotlin 2.4, Jetpack Compose (Material 3), AGP 9, qrcode-kotlin (g0dkar), KotNexLib, Klogger. ZXing nur im Test als unabhängiger Prüfer. |
| **Basis** | Konventionen aus [`nexus421/DemoAiProject`](https://github.com/nexus421/DemoAiProject) (`CLAUDE.md`, `README.md`, `ANDROID.md`, Stand Commit `f8ef737`, 15.10.2026). |
| **Stand** | 05.11.2026 |

---

## Inhalt

1. [Was die App kann](#1-was-die-app-kann)
2. [Das Display: verifizierte Fakten](#2-das-display-verifizierte-fakten)
3. [Beweis: was geprüft wurde und wie](#3-beweis-was-geprüft-wurde-und-wie)
4. [Entscheidungen (KISS)](#4-entscheidungen-kiss)
5. [Bedienkonzept (idiotensicher)](#5-bedienkonzept-idiotensicher)
6. [Globale Vorgaben](#6-globale-vorgaben)
7. [Projektstruktur](#7-projektstruktur)
8. [Review-Fokus](#8-review-fokus)
9. [Tasks](#9-tasks)
10. [Fehlersuche](#10-fehlersuche)
11. [Quellen](#11-quellen)
- [Anhang A: Kern-Code (verifiziert)](#anhang-a-kern-code-verifiziert)
- [Anhang B: Tests (verifiziert)](#anhang-b-tests-verifiziert)
- [Anhang C: Android-Plattformcode (gegen API-Stubs kompiliert)](#anhang-c-android-plattformcode)
- [Anhang D: Protokoll-Referenz](#anhang-d-protokoll-referenz)

---

## 1. Was die App kann

Sieben Modi, jeder ist ein Chip auf dem einen Screen:

| Modus | Inhalt auf dem Display (250 × 122 px, quer) | Eingaben |
|---|---|---|
| **QR** | Ein QR-Code, zentriert, so groß wie möglich | Inhalt |
| **QR + Notiz** | QR links (122 × 122), freier Text rechts | Inhalt, Notiz |
| **QR + Inhalt** | QR links, sein eigener Inhalt als lesbarer Text rechts | Inhalt |
| **2× QR** | Zwei QR-Codes nebeneinander | Inhalt links, Inhalt rechts |
| **Text** | Nur Text, automatisch so groß wie möglich, mit Zeilenumbruch | Text |
| **Barcode** | EAN-13 oder Code 128 (automatisch erkannt), Wert optional darunter | Wert, Schalter „Wert anzeigen“ |
| **Bild** | Foto oder Grafik, schwarz-weiß gerastert | Bild aus Photo Picker, Schalter „Ausfüllen“ und „Foto-Modus“ |

Dazu: Vorschau in Echtzeit (exakt die Pixel, die das Display zeigen wird), automatisches Schreiben beim
Dranhalten, Fortschrittsbalken, Vibration bei Erfolg, verständliche Fehlermeldungen, Testbild zur Kalibrierung,
Schalter „Um 180° drehen“, letzte Eingaben bleiben gespeichert.

**Grenzen (getestet, siehe Anhang B):**

| Was | Grenze |
|---|---|
| QR (jeder QR-Modus) | 213 Byte UTF-8 (Umlaut = 2 Byte, Emoji = 4 Byte). Kleinstes Modul 2 px = 0,39 mm |
| Barcode Code 128 | 17 Zeichen (druckbares ASCII), nur Ziffern: 34 |
| Barcode EAN-13 | 13 Ziffern mit gültiger Prüfziffer |
| Text | Schriftgröße 10 bis 96 px, sonst Fehler „zu lang“ |

---

## 2. Das Display: verifizierte Fakten

Das Gerät auf dem Foto ist das **Waveshare 2.13inch Passive NFC-Powered e-Paper** (Amazon-Händler „IBest“):
250 × 122 px, Schwarz/Weiß, kein Akku, Strom und Daten kommen per NFC vom Handy.

| Fakt | Wert | Belegt durch |
|---|---|---|
| Tag-Typ | ISO 14443-3A, Android `NfcA`, UID ASCII `WSDZ10m` | Proxmark3 `cmdhfwaveshare.c` (prüft UID), NFC E-Paper Writer (`WaveShareUID`) |
| Befehlsformat | `CD <cmd> [args]`, Antwort `00 00` = OK | SDK-Bytecode (`NFC.jar`, Klasse `a`), Proxmark3 |
| CRC | Android hängt CRC selbst an, nicht mitsenden | Android-Doku `NfcA.transceive` |
| Panel-ID 2.13" | `CD 00 04` | SDK-Tabelle `h[1] = 4`, Proxmark3 `EPD_2IN13V2 = 4` |
| Bildspeicher | 250 Zeilen × 16 Byte = 4000 Byte, hochkant, 128 Spalten, davon 122 sichtbar | SDK (Tabellen `f[1]=19`, `g[1]=250`), Proxmark3 (`16, 122, 250`) |
| Bit-Bedeutung | `1` = weiß, `0` = schwarz, MSB = linkes Pixel | SDK: `(pixel & 0xFF) > 128` setzt 1, Proxmark3: schwarz wird invertiert |
| Ausrichtung | SDK nimmt 250 × 128 quer und dreht um 270°: Querpixel (x, y) landet in Zeile 249−x, Spalte y | SDK-Bytecode `Matrix.setRotate(270f)` |
| Datenpaket | `CD 08 10` + 16 Byte, 250 Pakete, 2 ms Pause | SDK, Proxmark3 |
| Ablauf | `0D`, `00 04`, `01`, `02`, `03`, `05`, `06`, `07 00`, 250 × `08`, `18`, `09`, Abfrage `0A` bis `FF 00`, `04` | SDK-Bytecode, identisch zu Proxmark3 für 2.13" |
| Wiederholen | Antwort ≠ `00 00`: bis zu 10 Mal neu senden | Proxmark3 `transceive_blocking` |
| Timeout | SDK 700 ms, NFC E-Paper Writer erhöht auf 1200 ms | SDK-Bytecode, `WaveShareHandler.kt` |

**Was nicht ohne Hardware zu klären ist** (deshalb gibt es das Testbild, Task 8):

1. Welche Querkante physisch „oben“ ist. Das SDK geht von der hier umgesetzten Ausrichtung aus. Falls verkehrt: Schalter „Um 180° drehen“.
2. Ob die 6 unsichtbaren Speicherspalten am Ende liegen (wie im offiziellen Waveshare-Treiber `EPD_2in13_V2`, wo Spalte 0 bis 121 vom MSB an belegt sind) oder am Anfang. Das Testbild zeigt es: fehlt eine Rahmenlinie, siehe [Fehlersuche](#10-fehlersuche).
3. Wie lange ein konkretes Handy braucht. Erwartung aus den Pausen im Protokoll: etwa 2 s Übertragung plus 2 s Refresh.

> **Warum nicht das offizielle SDK (`NFC.jar`)?** Die Hauptklasse ist package-private und obfuskiert, es gibt
> keine Lizenz, und das SDK schreibt ungefragt einen NDEF-Eintrag (AAR `waveshare.feng.nfctag`) auf den Tag.
> Das Protokoll ist dagegen klein (13 Befehlsarten) und hier vollständig nachgebaut und getestet.

---

## 3. Beweis: was geprüft wurde und wie

Geprüft wurde in einer Linux-Umgebung mit **Kotlin 2.4.21** (JetBrains-Release), **JUnit 4.13.2**,
**qrcode-kotlin 4.5.0** (aus dem Quell-Tag gebaut), **ZXing 3.5.4** (aus dem Quell-Tag gebaut) und den
`ResultOf`-Typen aus **KotNexLib 4.4.1** (Quell-Tag).

| Prüfung | Ergebnis |
|---|---|
| Alle 36 Tests aus Anhang B | **OK (36 tests)** |
| `FramePacker` gegen eine wörtliche Nachbildung der SDK-Pipeline (Drehung + Packen aus dem Bytecode), 20 Zufallsbilder | identisch, Byte für Byte |
| Jedes Layout: rendern, packen, wie das Panel zurückrechnen, mit **ZXing** dekodieren (QR, Umlaute, Emoji, WLAN, 2× QR, Maximallänge, EAN-13, Code 128 gerade/ungerade Ziffern, um 180° gedreht) | alles gelesen |
| Code-128-Tabelle gegen ZXings `Code128Reader.CODE_PATTERNS` | identisch (107 Muster) |
| Protokoll gegen simuliertes Display: Befehlsfolge, Pausen, empfangene 4000 Byte, Wiederholung, Abbruch (TagLost), Timeout | wie erwartet |
| **Mutationstests:** ein Code-128-Muster vertauscht, Drehung entfernt, EAN-Parität vertauscht, Protokollschritt gelöscht | jeweils **rot** (1, 7, 1 und 3 fehlschlagende Tests). Die Tests erkennen also echte Fehler |
| Android-Code aus Anhang C gegen Signatur-Stubs der benutzten Android-APIs | kompiliert |
| Android-APIs (`NfcA`, `enableReaderMode`, Photo Picker, `HapticFeedbackType.Confirm`) | gegen developer.android.com geprüft |

**Ehrliche Grenzen des Beweises:**

- Es gab kein Android SDK und kein Gerät. Der Android-Teil (Anhang C, Tasks 5 bis 7) ist gegen Stubs kompiliert, aber nicht gegen das echte `android.jar` und nicht auf Hardware getestet. Das erledigen `./gradlew assembleDebug` (Task 1 und 7) und der Hardware-Test (Task 8).
- Das Protokoll stammt aus SDK und Proxmark3, beide liefen nicht gegen das echte Display. Proxmark3 markiert das 2.13"-Modell als „tested“.
- Die Versionsnummern in Abschnitt 6 sind der Stand der offiziellen Release Notes. Wie `ANDROID.md` verlangt: vor dem Start auf neuere prüfen.

Vorschau der Layouts, gerendert mit genau diesem Code (Text dort mit AWT, in der App mit `StaticLayout`):
siehe die mitgelieferte Datei `layouts.png`.

---

## 4. Entscheidungen (KISS)

| Entscheidung | Warum | Verworfen |
|---|---|---|
| **Eigenes Protokoll statt `NFC.jar`** | Klein, testbar, keine Black Box, kein Lizenzproblem | Offizielles SDK |
| **qrcode-kotlin 4.5.0 (g0dkar) für QR** | Reines Kotlin (Multiplatform), MIT, liefert über `QRCodeProcessor.encode()` direkt die Modulmatrix. Wir zeichnen selbst pixelgenau | ZXing (Java), Rendern über die Bitmap-API der Library (würde skalieren und unscharf machen) |
| **Eigener Barcode-Encoder (EAN-13, Code 128 B/C, ca. 70 Zeilen)** | qrcode-kotlin kann keine Strichcodes. Eine zweite Library nur dafür lohnt nicht. Korrektheit beweist ZXing im Test | ZXing als Laufzeit-Abhängigkeit |
| **ZXing nur `testImplementation`** | Unabhängiger Decoder: Was ZXing aus 250 × 122 px liest, liest auch eine Handykamera | Selbstgeschriebener Decoder |
| **Ganze Pixel pro Modul** | Ein QR-Modul ist immer 2 px oder mehr, Barcodestriche ganzzahlig. Verzerrte Module machen Codes unlesbar | Freies Skalieren |
| **Ruhezone 2 Module** beim QR | Der weiße Gehäuserahmen verlängert sie physisch. So wird der QR größer. ZXing liest es (getestet) | 4 Module laut Norm |
| **Reader Mode** (`enableReaderMode`) | Kein Intent-Filter, kein Foreground Dispatch, Android öffnet nicht den Play Store für den AAR-Eintrag | Foreground Dispatch (wie NFC E-Paper Writer) |
| **UID wird nicht geprüft** | Der NFC E-Paper Writer berichtet, dass `WSDZ10m` manchmal verfälscht ankommt. Maßgeblich ist, ob `CD 0D` mit `00 00` beantwortet wird. Andere Karten (z. B. Bankkarten) antworten nicht so und lösen nur eine Fehlermeldung aus | UID-Filter |
| **Schreiben automatisch beim Dranhalten** | Kein Knopf, den man vergessen kann. Ist der Inhalt fehlerhaft, kommt eine klare Meldung | „Senden“-Button plus Dranhalten |
| **Ein Screen, keine Navigation 3** | Es gibt genau ein Ziel. `ANDROID.md`-Muster (Contract, ViewModel, View) bleibt | Navigation 3 mit einer Route |
| **Keine Datenbank** | Nur ein Satz letzter Eingaben, `SharedPreferences` reichen (`ANDROID.md`: Settings) | ObjectBox |
| **Fortschritt inline** (`LinearProgressIndicator`) | Kein Dialog nötig, Status ist immer sichtbar. Spart eine Abhängigkeit | ComposeLoadingDialog |
| **KotNexLib für `ResultOf2`/`ResultOfEmpty`** | Hauskonvention. Achtung: zieht BouncyCastle und kotlin-reflect mit. R8 entfernt Ungenutztes im Release | Eigene sealed Results (wären 10 Zeilen, aber gegen die Konvention) |
| **Scannen von Codes optional** (Task 9, `BarcodeScanner 3.0.0`) | Kundenkarte abscannen statt abtippen ist der idiotensicherste Weg. Bringt aber CameraX und ML Kit mit, daher optional | Pflicht |
| **`minSdk 35`** | `ANDROID.md`-Vorgabe für neue Apps, `ImageDecoder` und Dynamic Color ohne Fallbacks | niedriger |

---

## 5. Bedienkonzept (idiotensicher)

```
┌──────────────────────────────────────┐
│ InkTap                            ⋮  │  ⋮ = [✓] Um 180° drehen · Testbild senden
├──────────────────────────────────────┤
│ ┌──────────────────────────────────┐ │
│ │        VORSCHAU 250 × 122        │ │  exakt die Display-Pixel, ohne Glättung
│ │   (oder grauer Hinweis / Fehler) │ │
│ └──────────────────────────────────┘ │
│  Fehlerzeile (rot) oder Hinweis      │
│                                      │
│ [QR] [QR + Notiz] [QR + Inhalt]      │  FlowRow, alle Chips immer sichtbar
│ [2× QR] [Text] [Barcode] [Bild]      │
│                                      │
│ ┌ Inhalt ──────────────────────────┐ │  Felder je nach Modus
│ └──────────────────────────────────┘ │
├──────────────────────────────────────┤
│ 📶  Bereit. Halte die Rückseite      │  Statuskarte, immer unten sichtbar
│     deines Handys ans Display.       │
│ ▓▓▓▓▓▓▓▓▓▓░░░░░░  (nur beim Senden)  │
└──────────────────────────────────────┘
```

**Regeln:**

1. Die Vorschau ist immer die Wahrheit. Was dort steht, kommt aufs Display.
2. Ist der Inhalt leer, zeigt die Vorschau einen grauen Hinweis (kein Rot). Rot nur bei echten Fehlern.
3. Dranhalten schreibt sofort. Läuft schon ein Schreibvorgang, wird ein zweiter Tag ignoriert.
4. Jede Meldung sagt, was zu tun ist („nochmal ruhig dranhalten“), nie nur, was schiefging.
5. Erfolg: Vibration `Confirm` plus grüner Haken. Fehler: Vibration `Reject`.
6. Jede Eingabe setzt einen alten Status („Fertig“, Fehler) zurück auf „Bereit“.
7. NFC aus: Statuskarte mit Button „NFC einschalten“ (öffnet `Settings.ACTION_NFC_SETTINGS`). Kein NFC im Handy: Hinweis, Rest der App funktioniert (Vorschau).

**Texte** (`values/strings.xml`, Deutsch. `values-en/strings.xml` sinngemäß Englisch). Keine Gedankenstriche, keine Semikolons (Hauskonvention):

| Key | Deutsch |
|---|---|
| `app_name` | InkTap |
| `mode_qr` / `mode_qr_note` / `mode_qr_content` / `mode_two_qr` / `mode_text` / `mode_barcode` / `mode_picture` | QR / QR + Notiz / QR + Inhalt / 2× QR / Text / Barcode / Bild |
| `field_qr` / `field_qr_left` / `field_qr_right` / `field_note` / `field_text` / `field_barcode` | Inhalt (Link, Text, WLAN …) / Inhalt links / Inhalt rechts / Notiz / Text / Barcode-Wert |
| `switch_show_value` | Wert unter dem Barcode anzeigen |
| `barcode_detected` | Erkannt: %1$s |
| `pick_picture` | Bild auswählen |
| `switch_crop` | Ausfüllen (Ränder abschneiden) |
| `switch_dither` | Foto-Modus (Graustufen rastern) |
| `menu_rotate` / `menu_test_pattern` | Um 180° drehen / Testbild senden |
| `hint_empty` | Gib oben etwas ein, dann erscheint hier die Vorschau. |
| `hint_test_pattern` | Testbild bereit. Halte das Handy ans Display. |
| `error_qr_too_long` | Zu viel Inhalt für einen QR-Code. Maximal etwa 200 Zeichen. |
| `error_barcode_too_long` | Zu lang für einen Barcode. Maximal 17 Zeichen, nur Ziffern 34. Nimm sonst einen QR-Code. |
| `error_barcode_chars` | Barcodes können keine Umlaute und Sonderzeichen. Nimm einen QR-Code. |
| `error_text_too_long` | Der Text ist zu lang für das Display. |
| `nfc_ready` | Bereit. Halte die Rückseite deines Handys ans Display. |
| `nfc_writing` | Übertrage … Handy nicht bewegen. |
| `nfc_done` | Fertig! Du kannst das Handy wegnehmen. |
| `nfc_fix_content` | Erst den Inhalt oben korrigieren, dann nochmal dranhalten. |
| `nfc_lost` | Verbindung abgebrochen. Halte das Handy ruhig ans Display und versuch es nochmal. |
| `nfc_rejected` | Das Display hat nicht mitgespielt. Bitte nochmal dranhalten. |
| `nfc_timeout` | Das Display hat das Bild nicht bestätigt. Prüfe es und halte das Handy bei Bedarf nochmal dran. |
| `nfc_off` / `nfc_open_settings` | NFC ist ausgeschaltet. / NFC einschalten |
| `nfc_missing` | Dieses Handy hat kein NFC. Die Vorschau funktioniert trotzdem. |
| `picture_failed` | Das Bild konnte nicht geladen werden. |

---

## 6. Globale Vorgaben

Gilt für jeden Task.

**Konventionen** (aus `CLAUDE.md`, `README.md`, `ANDROID.md` des DemoAiProject, verbindlich):

- `.not()` statt `!`. Kleine `if` ohne Klammern, Bedingung erst in ein `val`. `if`/`when` als Ausdruck.
- `runCatching` statt `try`/`catch`. Erwartete Ergebnisse als `ResultOf2`/`ResultOfEmpty` und sealed Typen, nie Exceptions.
- KDoc auf jeder öffentlichen Deklaration. Code, KDoc, Kommentare und Logs auf Englisch. UI-Texte in String-Ressourcen (Deutsch in `values/`, Englisch in `values-en/`).
- Keine Gedankenstriche (— und –) und keine Semikolons in Texten, KDoc, Kommentaren, Strings und Tests.
- Klassen loggen über `infoLog { }` und Co., Top-Level-Code über `staticLog` mit `private const val TAG`. Keine Inhalte loggen, die Passwörter enthalten können (WLAN-QR!). Logge nur Modus und Länge.
- Zeit und Zufall injizierbar (siehe `EpdWriter(sleep, clock)`).
- Eine Activity (`ComponentActivity`, `enableEdgeToEdge()`), kein DI, keine Fragmente, kein XML-Layout.
- Material 3 mit Dynamic Color: `if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)`.

**Versionen** (Stand der offiziellen Release Notes, vor Start auf neuere prüfen):

| Komponente | Version | Hinweis |
|---|---|---|
| Kotlin | `2.4.21` | neuester stabiler Tag im JetBrains-Repo. Über `kotlin-gradle-plugin` im Root-`buildscript` anheben (`ANDROID.md`) |
| Android Gradle Plugin | `9.3.3` | unterstützt API 37, braucht Gradle ≥ 9.5.0 |
| Gradle | `9.6.0` | wie DemoAiProject |
| Gradle-Daemon-JDK | Corretto 25 | wie DemoAiProject (AGP selbst braucht mindestens JDK 17) |
| `compileSdk` / `targetSdk` / `minSdk` | `37` / `37` / `35` | |
| Java-Bytecode | `JavaVersion.VERSION_17` | `ANDROID.md` |
| Compose BOM | `2026.09.00` | |
| `androidx.activity:activity-compose` | `1.13.0` | |
| `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.11.0` | |
| `io.github.g0dkar:qrcode-kotlin` | `4.5.0` | |
| `bayern.kickner:KotNexLib` | `4.4.1` | Repo `nexus421MavenReleases` |
| `bayern.kickner:Klogger` | `0.5.0` | Repo `nexus421MavenReleases` |
| `com.google.zxing:core` | `3.5.4` | **nur** `testImplementation` |
| `bayern.kickner:BarcodeScanner` | `3.0.0` | nur Task 9 (optional) |

**Package und IDs:** `applicationId = "bayern.kickner.inktap"`, `namespace = "bayern.kickner.inktap"`,
`versionName = "1.0.0"`, `versionCode` daraus berechnet wie in `ANDROID.md`.

---

## 7. Projektstruktur

```
InkTap/
├── settings.gradle.kts            Repos wie ANDROID.md, rootProject.name = "InkTap"
├── build.gradle.kts               buildscript-Classpath kotlin-gradle-plugin 2.4.21
├── gradle/libs.versions.toml      alle Versionen aus Abschnitt 6
├── gradle.properties
├── gradle-daemon-jvm.properties   Daemon-JDK pinnen (ANDROID.md)
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── keepRules/inktap.keep
        │   ├── res/values/strings.xml, res/values-en/strings.xml, res/xml/data_extraction_rules.xml, res/xml/backup_rules.xml
        │   └── kotlin/bayern/kickner/inktap/
        │       ├── InkTapApp.kt              Application: Klogger einrichten
        │       ├── MainActivity.kt           Theme, Reader Mode, NFC-Status
        │       ├── core/                     ANHANG A, ohne Android-Imports
        │       │   ├── MonoImage.kt          Display, MonoImage, GrayImage
        │       │   ├── FramePacker.kt        Bild → 4000 Byte
        │       │   ├── EpdWriter.kt          NFC-Protokoll, WriteError
        │       │   ├── Codes.kt              QR (qrcode-kotlin), EAN-13, Code 128
        │       │   ├── Layouts.kt            Content, ContentError, render(), largestFitting()
        │       │   └── Pictures.kt           fitInto(), Dithering, lumaOf()
        │       ├── platform/                 ANHANG C
        │       │   ├── AndroidText.kt        TextRasterizer mit StaticLayout
        │       │   └── PictureLoader.kt      ImageDecoder → GrayImage
        │       ├── nfc/
        │       │   └── TagWriter.kt          ANHANG C: NfcA verbinden, EpdWriter aufrufen
        │       ├── data/
        │       │   └── InkPrefs.kt           letzte Eingaben in SharedPreferences
        │       └── ui/screens/ink/
        │           ├── InkContract.kt        InkState, InkAction, Mode, NfcStatus
        │           ├── InkViewModel.kt
        │           └── InkView.kt            stateless Compose-UI
        └── test/kotlin/bayern/kickner/inktap/core/   ANHANG B
```

---

## 8. Review-Fokus

Fälle, die kein Unit-Test abdeckt, die aber echte Nutzer treffen. Jeder Punkt hat einen Prüfschritt im genannten Task.

1. **Handy wird mitten im Schreiben weggezogen.** Erwartung: Meldung `nfc_lost`, kein Absturz, nächster Versuch funktioniert. (Task 8, Schritt 4)
2. **Zweites Dranhalten während des Schreibens.** Erwartung: wird ignoriert, Fortschritt läuft weiter. (Task 6, Test `secondTagWhileWritingIsIgnored`)
3. **Langer Link ohne Leerzeichen in „QR + Inhalt“.** Erwartung: Text wird zeichenweise umbrochen statt „zu lang“. (Task 5, Schritt 3, manuell in der Vorschau)
4. **Riesiges Foto (50 MP) oder kaputte Datei.** Erwartung: kein OOM, bei kaputter Datei `picture_failed`. (Task 7, Schritt 5)
5. **App mit NFC aus starten, dann in den Einstellungen einschalten und zurück.** Erwartung: Status springt ohne Neustart auf „Bereit“. (Task 7, Schritt 6)

---

## 9. Tasks

### Task 1: Projektgerüst

**Files:**
- Create: alle Gradle-Dateien aus Abschnitt 7, `app/src/main/AndroidManifest.xml`, `InkTapApp.kt`, `res/xml/*`, `keepRules/inktap.keep`
- Create: `.github/workflows/build.yml` mit `./gradlew test assembleDebug` (wie DemoAiProject)

**Interfaces:**
- Produces: lauffähiges leeres Compose-Projekt, Klogger konfiguriert.

- [ ] **Schritt 1: Version Catalog anlegen** (`gradle/libs.versions.toml`)

```toml
[versions]
agp = "9.3.3"
kotlin = "2.4.21"
composeBom = "2026.09.00"
activityCompose = "1.13.0"
lifecycle = "2.11.0"
qrcodeKotlin = "4.5.0"
kotnexlib = "4.4.1"
klogger = "0.5.0"
zxing = "3.5.4"
junit = "4.13.2"

[libraries]
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx-compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
qrcode-kotlin = { group = "io.github.g0dkar", name = "qrcode-kotlin", version.ref = "qrcodeKotlin" }
kotnexlib = { group = "bayern.kickner", name = "KotNexLib", version.ref = "kotnexlib" }
klogger = { group = "bayern.kickner", name = "Klogger", version.ref = "klogger" }
kotlin-test-junit = { group = "org.jetbrains.kotlin", name = "kotlin-test-junit", version.ref = "kotlin" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
zxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxing" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Schritt 2: `app/build.gradle.kts`.** Plugins `android.application` und `kotlin.compose` (kein `org.jetbrains.kotlin.android`, AGP 9 hat Kotlin eingebaut). `buildFeatures { compose = true, buildConfig = true }`, Build-Types wie `ANDROID.md` (`.debug`-Suffix, Release mit R8). Abhängigkeiten:

```kotlin
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.qrcode.kotlin)
    implementation(libs.kotnexlib)
    implementation(libs.klogger)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit)
    testImplementation(libs.zxing.core)
}

android {
    // Unit tests render text with AWT, see TestSupport.kt.
    testOptions.unitTests.all { it.systemProperty("java.awt.headless", "true") }
}
```

- [ ] **Schritt 3: Manifest.** NFC ist Pflicht, sonst ergibt die App keinen Sinn:

```xml
<uses-permission android:name="android.permission.NFC" />
<uses-feature android:name="android.hardware.nfc" android:required="true" />
```

Eine Activity `MainActivity` mit `android:exported="true"` und Launcher-Intent-Filter. **Keine** NFC-Intent-Filter (Reader Mode braucht keine). `dataExtractionRules` und `fullBackupContent` schließen die `SharedPreferences` `inktap` aus (Settings bleiben laut `ANDROID.md` aus dem Backup, und die Eingaben können WLAN-Passwörter enthalten).

- [ ] **Schritt 4: `InkTapApp.kt`.** `Application`, in `onCreate()` `KLogger.configure { logToConsole(); debug = BuildConfig.DEBUG }`. Im Manifest als `android:name` eintragen.

- [ ] **Schritt 5: Bauen.** Run: `./gradlew assembleDebug` · Expected: `BUILD SUCCESSFUL`

- [ ] **Schritt 6: Commit.** `git commit -m "chore: project skeleton"`

---

### Task 2: Kern: MonoImage und FramePacker

**Files:**
- Create: `core/MonoImage.kt`, `core/FramePacker.kt` (wörtlich aus Anhang A)
- Test: `test/.../core/TestSupport.kt`, `FramePackerTest.kt` (wörtlich aus Anhang B)

**Interfaces:**
- Produces: `object Display { WIDTH = 250, HEIGHT = 122 }`, `class MonoImage(width, height)` mit `get`, `set`, `fillRect`, `draw`, `toArgb`, `blackCount`, `class GrayImage(width, height, luma: IntArray)`, `object FramePacker { fun pack(image: MonoImage, rotate180: Boolean = false): ByteArray }` (4000 Byte).

> `TestSupport.kt` braucht schon alle Kern-Dateien. Lege in diesem Task daher **alle** Dateien aus Anhang A an, prüfe aber nur `FramePackerTest`. Tasks 3 und 4 prüfen den Rest.

- [ ] **Schritt 1:** Alle Dateien aus Anhang A und B anlegen.
- [ ] **Schritt 2:** Run: `./gradlew :app:testDebugUnitTest --tests "*FramePackerTest"` · Expected: 5 Tests grün.
- [ ] **Schritt 3: Gegenprobe.** In `FramePacker.pack` `val x = Display.WIDTH - 1 - row` testweise zu `val x = row` ändern, Test laufen lassen: muss rot werden (`matchesTheWaveshareSdkPipeline`). Änderung zurücknehmen.
- [ ] **Schritt 4: Commit.** `git commit -m "feat(core): frame packing for the 2.13 inch panel"`

### Task 3: Kern: NFC-Protokoll

**Files:** `core/EpdWriter.kt` (Anhang A, schon angelegt), Test `EpdWriterTest.kt`

**Interfaces:**
- Produces: `class EpdWriter(transceive: (ByteArray) -> ByteArray, sleep: (Long) -> Unit = Thread::sleep, clock: () -> Long = System::currentTimeMillis)` mit `fun write(frame: ByteArray, onProgress: (Float) -> Unit = {}): ResultOfEmpty<WriteError>`. `sealed interface WriteError { ConnectionLost, Rejected, RefreshTimeout }`, jeweils mit `step: String`.

- [ ] **Schritt 1:** Run: `./gradlew :app:testDebugUnitTest --tests "*EpdWriterTest"` · Expected: 5 Tests grün.
- [ ] **Schritt 2: Commit.** `git commit -m "feat(core): Waveshare NFC protocol"`

### Task 4: Kern: Codes, Layouts, Bilder

**Files:** `core/Codes.kt`, `core/Layouts.kt`, `core/Pictures.kt` (Anhang A), Tests `LayoutTest.kt`, `PictureTest.kt`

**Interfaces:**
- Produces: `fun render(content: Content, text: TextRasterizer): ResultOf2<MonoImage, ContentError>`, `sealed interface Content { Qr, QrWithNote, QrWithContent, TwoQr, Text, Barcode, Picture, TestPattern }`, `sealed interface ContentError { Empty, QrTooLong, BarcodeTooLong, BarcodeInvalidChars, TextTooLong }`, `fun interface TextRasterizer { fun rasterize(text: String, width: Int, height: Int): MonoImage? }`, `fun encodeBarcode(value: String): ResultOf2<BarcodeModules, ContentError>` (mit `.format: BarcodeFormat { EAN_13, CODE_128 }`), `fun largestFitting(min: Int, max: Int, fits: (Int) -> Boolean): Int?`, `fun fitInto(srcW, srcH, crop): PixelRect`, `fun GrayImage.toMonoImage(dither: Boolean): MonoImage`, `fun lumaOf(argb: Int): Int`.

- [ ] **Schritt 1:** Run: `./gradlew :app:testDebugUnitTest` · Expected: **36 Tests grün** (alle Kern-Tests).
- [ ] **Schritt 2:** Prüfen, dass `core/` keinen `android.*`-Import enthält: `grep -r "import android" app/src/main/kotlin/bayern/kickner/inktap/core` · Expected: keine Ausgabe.
- [ ] **Schritt 3: Commit.** `git commit -m "feat(core): QR, barcode, text and picture layouts"`

---

### Task 5: Android-Plattform: Text, Bilder, NFC-Transport

**Files:** `platform/AndroidText.kt`, `platform/PictureLoader.kt`, `nfc/TagWriter.kt` (wörtlich aus Anhang C)

**Interfaces:**
- Consumes: `TextRasterizer`, `largestFitting`, `lumaOf`, `fitInto`, `GrayImage`, `EpdWriter`, `WriteError` aus Task 2 bis 4.
- Produces: `object AndroidText : TextRasterizer`, `fun decodePicture(context: Context, uri: Uri): Result<Bitmap>`, `fun toDisplayGray(picture: Bitmap, crop: Boolean): GrayImage`, `fun writeToTag(tag: Tag, frame: ByteArray, onProgress: (Float) -> Unit): ResultOfEmpty<WriteError>`.

- [ ] **Schritt 1:** Dateien anlegen, `./gradlew assembleDebug` · Expected: `BUILD SUCCESSFUL`.
- [ ] **Schritt 2:** Prüfen: `writeToTag` und `decodePicture` werden später **nur** aus `Dispatchers.IO` aufgerufen (blockierend, `NfcA.transceive` darf laut Doku nicht auf dem Main-Thread laufen).
- [ ] **Schritt 3 (Review-Fokus 3):** Nach Task 7 in der App „QR + Inhalt“ mit `https://www.example.com/ein/sehr/langer/pfad/ohne/leerzeichen` füllen. Expected: Vorschau zeigt umbrochenen Text, kein `error_text_too_long`.
- [ ] **Schritt 4: Commit.** `git commit -m "feat(platform): text rendering, picture loading, NFC transport"`

---

### Task 6: Contract und ViewModel

**Files:**
- Create: `ui/screens/ink/InkContract.kt`, `ui/screens/ink/InkViewModel.kt`, `data/InkPrefs.kt`
- Test: `test/.../ui/InkStateTest.kt`

**Interfaces:**
- Consumes: alles aus Task 2 bis 5.
- Produces (für Task 7):

```kotlin
/** The seven modes, one chip each. */
enum class Mode { QR, QR_NOTE, QR_CONTENT, TWO_QR, TEXT, BARCODE, PICTURE }

/** What the status card at the bottom shows. */
sealed interface NfcStatus {
    data object Missing : NfcStatus
    data object Off : NfcStatus
    data object Ready : NfcStatus
    data class Writing(val percent: Int) : NfcStatus
    data object Done : NfcStatus
    data object FixContentFirst : NfcStatus
    data class Failed(val error: WriteError) : NfcStatus
}

/** State of the only screen. [preview] is null exactly when [problem] is not null. */
data class InkState(
    val mode: Mode = Mode.QR,
    val qr: String = "",
    val qr2: String = "",
    val note: String = "",
    val text: String = "",
    val barcode: String = "",
    val showBarcodeValue: Boolean = true,
    val barcodeFormat: BarcodeFormat? = null,
    val hasPicture: Boolean = false,
    val pictureCrop: Boolean = true,
    val pictureDither: Boolean = true,
    val rotate180: Boolean = false,
    val testPattern: Boolean = false,
    val preview: MonoImage? = null,
    val problem: ContentError? = ContentError.Empty,
    val pictureFailed: Boolean = false,
    val nfc: NfcStatus = NfcStatus.Ready,
)

/** Everything the user (or the NFC callback) can do. */
sealed interface InkAction {
    data object Load : InkAction
    data class SelectMode(val mode: Mode) : InkAction
    data class EditQr(val value: String) : InkAction
    data class EditQr2(val value: String) : InkAction
    data class EditNote(val value: String) : InkAction
    data class EditText(val value: String) : InkAction
    data class EditBarcode(val value: String) : InkAction
    data class ShowBarcodeValue(val show: Boolean) : InkAction
    data class PickedPicture(val uri: Uri?) : InkAction
    data class PictureCrop(val crop: Boolean) : InkAction
    data class PictureDither(val dither: Boolean) : InkAction
    data class Rotate180(val rotate: Boolean) : InkAction
    data object ArmTestPattern : InkAction
    data class NfcChanged(val status: NfcStatus) : InkAction
    data class TagFound(val tag: Tag) : InkAction
}
```

**ViewModel-Regeln** (`class InkViewModel : ViewModel()`, `var state by mutableStateOf(InkState()); private set`, einziger Einstieg `fun onAction(action: InkAction, context: Context)`):

1. Jede Edit-Aktion: `state = state.copy(...)`, dann `state = state.withPreview(picture)`, dann `InkPrefs.save(context, state)`. Steht `nfc` auf `Done`, `Failed` oder `FixContentFirst`, wird es dabei zu `Ready`.
2. `withPreview` baut den `Content` (siehe unten) und ruft `render(content, AndroidText)`. Ergebnis: `preview` + `problem = null` oder `preview = null` + `problem`. Im Modus `BARCODE` zusätzlich `barcodeFormat` aus `encodeBarcode(barcode)`. Synchron auf Main ist in Ordnung (250 × 122 px, wenige Millisekunden).
3. `Load` liest `InkPrefs` nur beim ersten Aufruf (Flag `loaded`), danach Vorschau berechnen.
4. `PickedPicture(null)` ignorieren. Sonst `decodePicture` auf `Dispatchers.IO`. Bei Erfolg die `Bitmap` im privaten Feld `picture` halten (nicht im State, nicht persistiert), `hasPicture = true`, Vorschau neu. Bei Fehler `pictureFailed = true`.
5. `TagFound` ist der heikle Teil, so umsetzen:

```kotlin
private val busy = AtomicBoolean(false)

private fun write(tag: Tag) {
    val image = state.preview
    if (image == null) {
        state = state.copy(nfc = NfcStatus.FixContentFirst)
        return
    }
    val claimed = busy.compareAndSet(false, true)
    if (claimed.not()) return
    val frame = FramePacker.pack(image, state.rotate180)
    state = state.copy(nfc = NfcStatus.Writing(0))
    infoLog { "Writing mode=${state.mode} testPattern=${state.testPattern}" }
    viewModelScope.launch(Dispatchers.IO) {
        var lastPercent = 0
        val result = writeToTag(tag, frame) { progress ->
            val percent = (progress * 100).toInt()
            if (percent != lastPercent) {
                lastPercent = percent
                viewModelScope.launch(Dispatchers.Main) { state = state.copy(nfc = NfcStatus.Writing(percent)) }
            }
        }
        withContext(Dispatchers.Main) {
            state = when (result) {
                is ResultOfEmpty.Success -> state.copy(nfc = NfcStatus.Done, testPattern = false).withPreview(picture)
                is ResultOfEmpty.Failure -> state.copy(nfc = NfcStatus.Failed(result.value))
            }
            if (result is ResultOfEmpty.Failure) warnLog { "Write failed at ${result.value.step}" }
            busy.set(false)
        }
    }
}
```

6. `NfcChanged` übernimmt den Status, außer während `Writing`.
7. `ArmTestPattern`: `testPattern = true`, Vorschau zeigt `Content.TestPattern`. Nach erfolgreichem Schreiben wieder `false`.

**Content-Zuordnung** (eine private Funktion, `null` heißt „Bild fehlt“ und wird zu `problem = ContentError.Empty`):

```kotlin
private fun InkState.toContent(gray: GrayImage?): Content? = when {
    testPattern -> Content.TestPattern
    else -> when (mode) {
        Mode.QR -> Content.Qr(qr)
        Mode.QR_NOTE -> Content.QrWithNote(qr, note)
        Mode.QR_CONTENT -> Content.QrWithContent(qr)
        Mode.TWO_QR -> Content.TwoQr(qr, qr2)
        Mode.TEXT -> Content.Text(text)
        Mode.BARCODE -> Content.Barcode(barcode, showBarcodeValue)
        Mode.PICTURE -> gray?.let { Content.Picture(it, pictureDither) }
    }
}
```

`gray` ist `picture?.let { toDisplayGray(it, pictureCrop) }`.

**`InkPrefs`:** `object InkPrefs` mit `private const val FILE = "inktap"` und einem Key pro Feld (`mode` als Name des Enums, mit Fallback `Mode.QR`, so wie `ANDROID.md` Enums speichert). Gespeichert: `mode, qr, qr2, note, text, barcode, showBarcodeValue, pictureCrop, pictureDither, rotate180`. Nicht gespeichert: Bild, Vorschau, Status. `fun load(context: Context): InkState`, `fun save(context: Context, state: InkState)` über `core-ktx` `edit { }`.

- [ ] **Schritt 1: Test schreiben** (`InkStateTest.kt`, reine JVM, testet die Zuordnung über eine `internal`-Funktion `InkState.toContent(gray)`):
  - `qrModeMapsToQr`: `InkState(mode = Mode.QR, qr = "a").toContent(null) == Content.Qr("a")`
  - `pictureWithoutBitmapIsNull`: `InkState(mode = Mode.PICTURE).toContent(null) == null`
  - `testPatternWins`: `InkState(mode = Mode.TEXT, testPattern = true).toContent(null) == Content.TestPattern`
  - `secondTagWhileWritingIsIgnored` (Review-Fokus 2): `busy`-Logik in eine kleine `internal class WriteGate { fun tryEnter(): Boolean, fun leave() }` ziehen und testen: `tryEnter() == true`, zweites `tryEnter() == false`, nach `leave()` wieder `true`.
- [ ] **Schritt 2:** Run: `./gradlew :app:testDebugUnitTest --tests "*InkStateTest"` · Expected: FAIL (fehlt), dann implementieren, dann PASS.
- [ ] **Schritt 3: Commit.** `git commit -m "feat(ui): ink screen contract and view model"`

---

### Task 7: Activity und Compose-UI

**Files:** `MainActivity.kt`, `ui/screens/ink/InkView.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`

**Interfaces:**
- Consumes: `InkViewModel`, `InkState`, `InkAction`, `NfcStatus` aus Task 6.
- Produces: `@Composable fun InkView(state: InkState, onAction: (InkAction) -> Unit, onOpenNfcSettings: () -> Unit)` (stateless, previewbar).

**`MainActivity`** (so umsetzen, Reader-Mode-Details sind verifiziert):

```kotlin
class MainActivity : ComponentActivity() {

    private val vm: InkViewModel by viewModels()
    private val adapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.onAction(InkAction.Load, this)
        setContent {
            val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(this) else dynamicLightColorScheme(this)
            MaterialTheme(colorScheme = scheme) {
                InkView(
                    state = vm.state,
                    onAction = { vm.onAction(it, this) },
                    onOpenNfcSettings = { startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val nfc = adapter
        val status = when {
            nfc == null -> NfcStatus.Missing
            nfc.isEnabled.not() -> NfcStatus.Off
            else -> NfcStatus.Ready
        }
        vm.onAction(InkAction.NfcChanged(status), this)
        // Only NFC-A, and no NDEF probing by the platform: the display is busy enough being powered by the field.
        nfc?.enableReaderMode(
            this,
            { tag -> runOnUiThread { vm.onAction(InkAction.TagFound(tag), this) } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
    }

    override fun onPause() {
        super.onPause()
        adapter?.disableReaderMode(this)
    }
}
```

`Settings` ist hier `android.provider.Settings`, darum heißt unser Objekt `InkPrefs`. Den Plattform-Ton beim Erkennen behalten wir (kein `FLAG_READER_NO_PLATFORM_SOUNDS`): Er sagt dem Nutzer „verbunden“.

**`InkView`-Aufbau** (Material 3, Vorgaben aus Abschnitt 5):

- `Scaffold` mit `TopAppBar` (Titel `app_name`, Overflow-Menü mit `DropdownMenuItem` für `menu_rotate` (mit Checkbox, sendet `Rotate180`) und `menu_test_pattern` (sendet `ArmTestPattern`)) und `bottomBar` = Statuskarte.
- Inhalt: `Column` mit `verticalScroll`, `imePadding()`, 16 dp Abstand:
  1. Vorschau-`Card`: `Image` aus `remember(state.preview) { Bitmap.createBitmap(preview.toArgb(), 250, 122, Bitmap.Config.ARGB_8888).asImageBitmap() }`, `filterQuality = FilterQuality.None`, `Modifier.fillMaxWidth().aspectRatio(250f / 122f)`. Ohne Vorschau: graue Fläche gleicher Größe mit `hint_empty`.
  2. Problemzeile: `ContentError.Empty` grau (`hint_empty`), alle anderen in `colorScheme.error`. `pictureFailed` zeigt `picture_failed`.
  3. `FlowRow` mit sieben `FilterChip`s.
  4. Felder je Modus (`OutlinedTextField`, `fillMaxWidth`). QR-Felder mehrzeilig (`maxLines = 4`), Barcode einzeilig mit `KeyboardType.Ascii`, darunter `barcode_detected` mit `EAN-13`/`Code 128`. Bild: `Button` `pick_picture` mit `rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia())` und `PickVisualMediaRequest(PickVisualMedia.ImageOnly)` (keine Berechtigung nötig), dazu zwei `Switch`-Zeilen.
- Statuskarte: Icon (`Icons.Default.Nfc`, `CheckCircle`, `ErrorOutline`), Text aus Abschnitt 5, bei `Writing` ein `LinearProgressIndicator(progress = { percent / 100f })`, bei `Off` ein `Button` `nfc_open_settings`.
- Haptik: `val haptic = LocalHapticFeedback.current` und `LaunchedEffect(state.nfc) { when (state.nfc) { NfcStatus.Done -> haptic.performHapticFeedback(HapticFeedbackType.Confirm) ... Failed -> Reject } }`.
- Fehlertexte: `when (error) { is WriteError.ConnectionLost -> nfc_lost, is WriteError.Rejected -> nfc_rejected, is WriteError.RefreshTimeout -> nfc_timeout }`. Exhaustives `when`, kein `else`.
- `@Preview` für drei Zustände: leer, QR mit Vorschau, `Writing(60)`.

- [ ] **Schritt 1:** Strings anlegen (beide Sprachen, Abschnitt 5).
- [ ] **Schritt 2:** `MainActivity` und `InkView` umsetzen.
- [ ] **Schritt 3:** Run: `./gradlew test assembleDebug` · Expected: `BUILD SUCCESSFUL`, alle Tests grün.
- [ ] **Schritt 4:** Run: `./gradlew assembleRelease` und App aus dem Release-Build starten (R8 entfernt sonst evtl. etwas Nötiges). Expected: Vorschau funktioniert in allen sieben Modi.
- [ ] **Schritt 5 (Review-Fokus 4):** Ein Foto mit über 40 MP wählen: kein Absturz, Vorschau erscheint. Eine kaputte Datei (z. B. umbenannte Textdatei, über „Dateien“ im Picker): Meldung `picture_failed`.
- [ ] **Schritt 6 (Review-Fokus 5):** NFC aus, App öffnen: Karte „NFC ist ausgeschaltet“ mit Button. Button, NFC an, zurück: Status „Bereit“ ohne Neustart.
- [ ] **Schritt 7: Commit.** `git commit -m "feat(ui): ink screen and NFC reader mode"`

---

### Task 8: Hardware-Abnahme (manuell, mit dem echten Display)

- [ ] **Schritt 1: Testbild.** Menü, „Testbild senden“, Handy mit der NFC-Stelle (meist Mitte oder oberes Drittel der Rückseite) flach aufs Display legen. Expected: Rahmen an allen vier Kanten, „OBEN“ oben.
  - „OBEN“ steht unten: Menü, „Um 180° drehen“ aktivieren, wiederholen.
  - Eine Rahmenlinie fehlt: siehe [Fehlersuche](#10-fehlersuche), Punkt 1.
- [ ] **Schritt 2: Jeder Modus einmal** schreiben und mit einer normalen Kamera-App scannen: QR (Link), QR + Notiz (WLAN-Code `WIFI:T:WPA;S:<SSID>;P:<Passwort>;;`, verbindet sich das Handy?), QR + Inhalt, 2× QR (beide einzeln scannbar?), Text, Barcode (EAN-13 einer Kundenkarte, an der Kasse oder mit einer Scanner-App), Bild (Foto-Modus an und aus).
- [ ] **Schritt 3: Zeit messen.** Vom Ton bis zur Vibration. Expected: unter 10 s. Notieren, welches Handy.
- [ ] **Schritt 4 (Review-Fokus 1):** Während der Übertragung das Handy wegziehen. Expected: `nfc_lost`, App läuft weiter, erneutes Dranhalten klappt.
- [ ] **Schritt 5:** Ergebnisse (Handy, Zeiten, Ausrichtung) in `README.md` unter „Getestete Geräte“ festhalten.

### Task 9 (optional): Code scannen statt tippen

Nur umsetzen, wenn gewünscht. Bringt `bayern.kickner:BarcodeScanner:3.0.0` (CameraX, ML Kit) und die Kamera-Berechtigung.

- Neben den Feldern `field_qr`, `field_qr_left`, `field_qr_right` und `field_barcode` ein `IconButton` (`Icons.Default.QrCodeScanner`).
- Die View bekommt einen weiteren Parameter `onScan: ((String) -> Unit) -> Unit`. `MainActivity` setzt ihn um: erst `Manifest.permission.CAMERA` per `ActivityResultContracts.RequestPermission` anfragen (die Library fragt nicht selbst, sie zeigt ohne Berechtigung nur einen Hinweis), dann `BarcodeScannerDialogV3(this) { codes -> codes.firstOrNull()?.rawValue?.let(onResult) }`. Der Dialog öffnet sich beim Konstruieren, es gibt kein `.show()`.
- Die Library deklariert `android.hardware.camera.any` als Pflicht-Feature. Im App-Manifest mit `tools:node="replace"` und `android:required="false"` überschreiben, damit Handys ohne Kamera die App trotzdem installieren können.
- Das Ergebnis geht in das Feld, dessen Button gedrückt wurde. EAN-13 erkennt der Encoder selbst wieder.

---

## 10. Fehlersuche

| Symptom | Ursache | Lösung |
|---|---|---|
| Testbild: Rahmenlinie an einer Längskante fehlt, an der anderen ist sie doppelt oder fehlt ein Streifen | Die 6 unsichtbaren Speicherspalten liegen am Anfang statt am Ende | In `FramePacker.pack` `val col` um 6 verschieben (`frame[row * ROW_BYTES + (col + 6) / 8]`, Bit `(col + 6) % 8`). Testbild erneut. `displayedImage` in `TestSupport.kt` gleich anpassen |
| Bild steht kopf | Ausrichtung des Gehäuses | Schalter „Um 180° drehen“ |
| Oft `nfc_lost` | Handy-Antenne nicht mittig, Hülle zu dick, Handy bewegt | Hülle ab, Handy flach auflegen, NFC-Position des Handys suchen (Herstellerangabe) |
| Übertragung bricht immer an derselben Stelle ab | Timeout zu knapp für dieses Handy | `NFC_TIMEOUT_MS` in `TagWriter.kt` auf 2000 erhöhen |
| `nfc_timeout`, Bild aber korrekt | Refresh dauert länger als 10 s | `REFRESH_TIMEOUT_MS` in `EpdWriter` erhöhen (Proxmark3 wartet bis zu 20 s) |
| Android öffnet den Play Store, wenn die App nicht offen ist | Das offizielle Waveshare-SDK hat einen AAR-Eintrag auf den Tag geschrieben | Harmlos. In InkTap tritt es nicht auf, weil Reader Mode aktiv ist |
| Rauschen auf dem Display | Abbruch mitten im Schreiben | Nochmal dranhalten, das Bild wird komplett neu geschrieben |
| QR scannt schlecht | Kamera zu nah (E-Paper spiegelt) | 10 bis 15 cm Abstand, leicht schräg |

---

## 11. Quellen

- Waveshare-SDK `NFC.jar` (Klassen `a` und `b`), mit `javap` disassembliert, aus [joshuatz/nfc-epaper-writer](https://github.com/joshuatz/nfc-epaper-writer) (`app/libs/waveshare-nfc/NFC.jar`), dort auch `WaveShareHandler.kt` und `Constants.kt`
- Proxmark3: [RfidResearchGroup/proxmark3 `client/src/cmdhfwaveshare.c`](https://github.com/RfidResearchGroup/proxmark3/blob/master/client/src/cmdhfwaveshare.c) (Commit `1b71dfb`, 02.11.2026)
- qrcode-kotlin 4.5.0: [g0dkar/qrcode-kotlin](https://github.com/g0dkar/qrcode-kotlin) (`QRCodeProcessor.encode()`, MIT)
- ZXing 3.5.4: [zxing/zxing](https://github.com/zxing/zxing) (Tag `zxing-3.5.4`)
- KotNexLib 4.4.1, Klogger 0.5.0, BarcodeScanner 3.0.0: [github.com/nexus421](https://github.com/nexus421)
- Android: [NfcA](https://developer.android.com/reference/kotlin/android/nfc/tech/NfcA.html), [NfcAdapter](https://developer.android.com/reference/android/nfc/NfcAdapter), [Photo Picker](https://developer.android.com/training/data-storage/shared/photopicker), [HapticFeedbackType](https://developer.android.com/reference/kotlin/androidx/compose/ui/hapticfeedback/HapticFeedbackType)
- Versionen: [AGP 9.3 Release Notes](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [AndroidX Stable Channel](https://developer.android.com/jetpack/androidx/versions/stable-channel)
- Hauskonventionen: [nexus421/DemoAiProject](https://github.com/nexus421/DemoAiProject) (`CLAUDE.md`, `README.md`, `ANDROID.md`)

---

## Anhang A: Kern-Code (verifiziert)

Pfad jeweils `app/src/main/kotlin/bayern/kickner/inktap/core/`. Wörtlich übernehmen.

### MonoImage.kt

`MonoImage.kt` · 72 Zeilen

```kotlin
package bayern.kickner.inktap.core

/**
 * Size of the Waveshare 2.13 inch NFC e-paper as the user sees it: landscape, 250 x 122 pixels.
 * Every layout is drawn in this orientation. [FramePacker] turns it into the panel's own memory layout.
 */
object Display {
    /** Visible width in pixels (landscape). */
    const val WIDTH = 250

    /** Visible height in pixels (landscape). */
    const val HEIGHT = 122
}

/**
 * A 1-bit image. `true` means black, `false` means white. New images are completely white.
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 */
class MonoImage(val width: Int, val height: Int) {

    private val pixels = BooleanArray(width * height)

    init {
        require(width > 0 && height > 0) { "Image size must be positive, was ${width}x$height" }
    }

    /** True if ([x], [y]) is black. Coordinates outside the image are white. */
    operator fun get(x: Int, y: Int): Boolean = inside(x, y) && pixels[y * width + x]

    /** Sets ([x], [y]) to black ([black] = true) or white. Coordinates outside the image are ignored. */
    operator fun set(x: Int, y: Int, black: Boolean) {
        if (inside(x, y)) pixels[y * width + x] = black
    }

    /** Fills the rectangle with its top left corner at ([x], [y]) in black. Clipped at the image border. */
    fun fillRect(x: Int, y: Int, w: Int, h: Int) {
        for (yy in y until y + h) for (xx in x until x + w) this[xx, yy] = true
    }

    /** Copies every black pixel of [source] into this image, with the source's top left corner at ([x], [y]). */
    fun draw(source: MonoImage, x: Int, y: Int) {
        for (yy in 0 until source.height) for (xx in 0 until source.width) {
            if (source[xx, yy]) this[x + xx, y + yy] = true
        }
    }

    /**
     * ARGB pixels for an on-screen preview, row by row. Defaults look like e-paper: dark gray ink on warm white.
     */
    fun toArgb(ink: Int = 0xFF222222.toInt(), paper: Int = 0xFFF4F4F0.toInt()): IntArray =
        IntArray(width * height) { if (pixels[it]) ink else paper }

    /** Number of black pixels, handy for tests and for detecting an empty result. */
    fun blackCount(): Int = pixels.count { it }

    private fun inside(x: Int, y: Int) = x in 0 until width && y in 0 until height
}

/**
 * An 8-bit grayscale image, `0` is black and `255` is white. Used as the input for [toMonoImage].
 *
 * @property width Width in pixels.
 * @property height Height in pixels.
 * @property luma One value per pixel, row by row.
 */
class GrayImage(val width: Int, val height: Int, val luma: IntArray) {
    init {
        require(luma.size == width * height) { "Expected ${width * height} values, got ${luma.size}" }
    }
}
```

### FramePacker.kt

`FramePacker.kt` · 45 Zeilen

```kotlin
package bayern.kickner.inktap.core

/**
 * Converts a landscape [MonoImage] of [Display.WIDTH] x [Display.HEIGHT] into the 4000 byte frame the
 * 2.13 inch panel expects.
 *
 * The panel memory is portrait: 250 rows of 16 bytes (128 pixel columns, of which the first 122 are visible).
 * A bit of `1` is white, `0` is black, the most significant bit is the leftmost pixel. This is exactly what the
 * official Waveshare SDK produces when it rotates a 250 x 128 landscape bitmap by 270 degrees and packs it,
 * see `FramePackerTest.matchesTheWaveshareSdkPipeline`.
 */
object FramePacker {

    /** Bytes per portrait row (128 columns). */
    const val ROW_BYTES = 16

    /** Number of portrait rows, equals [Display.WIDTH]. */
    const val ROWS = Display.WIDTH

    /** Size of a complete frame in bytes. */
    const val FRAME_BYTES = ROW_BYTES * ROWS

    /**
     * Packs [image] into a frame.
     *
     * @param rotate180 Turns the picture upside down, for users who hold the display the other way round.
     */
    fun pack(image: MonoImage, rotate180: Boolean = false): ByteArray {
        require(image.width == Display.WIDTH && image.height == Display.HEIGHT) {
            "Expected ${Display.WIDTH}x${Display.HEIGHT}, got ${image.width}x${image.height}"
        }
        // All white, which also fills the 6 invisible padding columns.
        val frame = ByteArray(FRAME_BYTES) { 0xFF.toByte() }
        for (row in 0 until ROWS) for (col in 0 until Display.HEIGHT) {
            val x = Display.WIDTH - 1 - row
            val y = col
            val black = if (rotate180) image[Display.WIDTH - 1 - x, Display.HEIGHT - 1 - y] else image[x, y]
            if (black) {
                val index = row * ROW_BYTES + col / 8
                frame[index] = (frame[index].toInt() and (0x80 ushr (col % 8)).inv()).toByte()
            }
        }
        return frame
    }
}
```

### EpdWriter.kt

`EpdWriter.kt` · 123 Zeilen

```kotlin
package bayern.kickner.inktap.core

import kotnexlib.ResultOfEmpty

/** Why writing to the display failed. Every case means: hold the phone to the display again. */
sealed interface WriteError {
    /** Name of the protocol step that failed, for logs. */
    val step: String

    /** The connection broke, usually because the phone moved or the tag is not a Waveshare display. */
    data class ConnectionLost(override val step: String, val cause: Throwable?) : WriteError

    /** The display answered, but not with "OK" (even after retries). */
    data class Rejected(override val step: String, val answer: String) : WriteError

    /** The image was sent, but the display never reported that the refresh finished. */
    data class RefreshTimeout(override val step: String = "wait for refresh") : WriteError
}

/**
 * Speaks the Waveshare NFC e-paper protocol for the 2.13 inch model (SDK type 1, panel id `4`).
 *
 * Every command is `0xCD <cmd> [args]` sent as a raw ISO 14443-3A frame, the display answers `00 00` for OK.
 * Sequence and delays come from the official Waveshare Android SDK (`NFC.jar`, class `a`) and match the
 * Proxmark3 implementation (`client/src/cmdhfwaveshare.c`), which retries a command up to 10 times when the
 * answer is not `00 00`. This class does the same.
 *
 * @param transceive Sends one frame and returns the answer. On Android this is `NfcA::transceive`, it throws
 * an `IOException` when the tag is gone.
 * @param sleep Pause in milliseconds, injectable for tests.
 * @param clock Current time in milliseconds, injectable for tests.
 */
class EpdWriter(
    private val transceive: (ByteArray) -> ByteArray,
    private val sleep: (Long) -> Unit = Thread::sleep,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    /**
     * Writes a frame from [FramePacker.pack] to the display and waits for the refresh.
     *
     * @param onProgress Called with values from `0f` to `1f`.
     */
    fun write(frame: ByteArray, onProgress: (Float) -> Unit = {}): ResultOfEmpty<WriteError> {
        require(frame.size == FramePacker.FRAME_BYTES) { "Frame must have ${FramePacker.FRAME_BYTES} bytes" }

        val setup = listOf(
            Step("init", byteArrayOf(0x0D), 0),
            Step("select panel", byteArrayOf(0x00, PANEL_2IN13), 50),
            Step("normal mode", byteArrayOf(0x01), 20),
            Step("config 1", byteArrayOf(0x02), 20),
            Step("power on", byteArrayOf(0x03), 20),
            Step("config 2", byteArrayOf(0x05), 20),
            Step("load to main", byteArrayOf(0x06), 10),
            Step("data preparation", byteArrayOf(0x07, 0x00), 0),
        )
        for (step in setup) {
            val error = command(step.name, step.payload)
            if (error != null) return ResultOfEmpty.Failure(error)
            if (step.pauseMs > 0) sleep(step.pauseMs)
        }

        for (row in 0 until FramePacker.ROWS) {
            val payload = byteArrayOf(0x08, FramePacker.ROW_BYTES.toByte()) +
                frame.copyOfRange(row * FramePacker.ROW_BYTES, (row + 1) * FramePacker.ROW_BYTES)
            val error = command("data row $row", payload)
            if (error != null) return ResultOfEmpty.Failure(error)
            onProgress((row + 1) * 0.9f / FramePacker.ROWS)
            sleep(2)
        }

        command("power on 2", byteArrayOf(0x18))?.let { return ResultOfEmpty.Failure(it) }
        sleep(200)
        command("refresh", byteArrayOf(0x09))?.let { return ResultOfEmpty.Failure(it) }
        sleep(300)

        waitUntilReady()?.let { return ResultOfEmpty.Failure(it) }
        onProgress(0.98f)
        command("power off", byteArrayOf(0x04))?.let { return ResultOfEmpty.Failure(it) }
        onProgress(1f)
        return ResultOfEmpty.Success
    }

    /** Polls `CD 0A` until the display answers `FF 00` (refresh done) or [REFRESH_TIMEOUT_MS] passed. */
    private fun waitUntilReady(): WriteError? {
        val deadline = clock() + REFRESH_TIMEOUT_MS
        while (clock() < deadline) {
            val answer = send(byteArrayOf(CMD_PREFIX, 0x0A)).getOrElse {
                return WriteError.ConnectionLost("wait for refresh", it)
            }
            val ready = answer.size >= 2 && answer[0] == 0xFF.toByte() && answer[1] == 0x00.toByte()
            if (ready) return null
            sleep(POLL_INTERVAL_MS)
        }
        return WriteError.RefreshTimeout()
    }

    /** Sends one command, retries while the answer is not `00 00`. Returns null on success. */
    private fun command(name: String, payload: ByteArray): WriteError? {
        var lastAnswer = ByteArray(0)
        repeat(MAX_ATTEMPTS) {
            val answer = send(byteArrayOf(CMD_PREFIX) + payload).getOrElse {
                return WriteError.ConnectionLost(name, it)
            }
            val ok = answer.size >= 2 && answer[0] == 0.toByte() && answer[1] == 0.toByte()
            if (ok) return null
            lastAnswer = answer
        }
        return WriteError.Rejected(name, lastAnswer.toHexString())
    }

    private fun send(frame: ByteArray): Result<ByteArray> = runCatching { transceive(frame) }

    private class Step(val name: String, val payload: ByteArray, val pauseMs: Long)

    private companion object {
        const val CMD_PREFIX = 0xCD.toByte()
        const val PANEL_2IN13: Byte = 4
        const val MAX_ATTEMPTS = 10
        const val REFRESH_TIMEOUT_MS = 10_000L
        const val POLL_INTERVAL_MS = 100L
    }
}
```

### Codes.kt

`Codes.kt` · 113 Zeilen

```kotlin
package bayern.kickner.inktap.core

import kotnexlib.ResultOf2
import qrcode.raw.ErrorCorrectionLevel
import qrcode.raw.QRCodeProcessor

/** Smallest QR module in pixels. 2 px are 0.39 mm on this panel, 1 px would be too small for most cameras. */
const val MIN_QR_MODULE_PX = 2

/** Quiet zone around a QR code in modules. The white frame of the display case adds more on the outside. */
const val QR_QUIET_MODULES = 2

/**
 * Encodes [text] as QR code (error correction M) and draws it as large as possible, centered, into a square of
 * [size] x [size] pixels. Every module gets the same whole number of pixels, otherwise scanners struggle.
 */
fun renderQr(text: String, size: Int): ResultOf2<MonoImage, ContentError> {
    if (text.isEmpty()) return ResultOf2.Failure(ContentError.Empty)
    val modules = runCatching {
        QRCodeProcessor(text, ErrorCorrectionLevel.MEDIUM).encode()
    }.getOrElse { return ResultOf2.Failure(ContentError.QrTooLong) }

    val count = modules.size
    val modulePx = size / (count + 2 * QR_QUIET_MODULES)
    if (modulePx < MIN_QR_MODULE_PX) return ResultOf2.Failure(ContentError.QrTooLong)

    val image = MonoImage(size, size)
    val offset = (size - count * modulePx) / 2
    for (row in 0 until count) for (col in 0 until count) {
        if (modules[row][col].dark) image.fillRect(offset + col * modulePx, offset + row * modulePx, modulePx, modulePx)
    }
    return ResultOf2.Success(image)
}

/** Barcode symbologies the app can draw. */
enum class BarcodeFormat { EAN_13, CODE_128 }

/**
 * A barcode as a row of modules (`true` = bar), quiet zones not included.
 *
 * @property quietLeft Quiet zone the standard demands on the left, in modules.
 * @property quietRight Quiet zone the standard demands on the right, in modules.
 */
class BarcodeModules(val format: BarcodeFormat, val bars: BooleanArray, val quietLeft: Int, val quietRight: Int)

/**
 * Picks the format automatically: 13 digits with a valid check digit become EAN-13 (the code on most customer
 * cards and products), everything else Code 128. Code 128 allows printable ASCII only (no umlauts).
 */
fun encodeBarcode(value: String): ResultOf2<BarcodeModules, ContentError> {
    if (value.isEmpty()) return ResultOf2.Failure(ContentError.Empty)
    val isEan13 = value.length == 13 && value.all { it in '0'..'9' } && ean13CheckDigit(value.take(12)) == value[12]
    if (isEan13) return ResultOf2.Success(BarcodeModules(BarcodeFormat.EAN_13, ean13(value), 11, 7))
    val printableAscii = value.all { it.code in 32..126 }
    if (printableAscii.not()) return ResultOf2.Failure(ContentError.BarcodeInvalidChars)
    return ResultOf2.Success(BarcodeModules(BarcodeFormat.CODE_128, code128(value), 10, 10))
}

/** Check digit of the first 12 digits of an EAN-13. */
fun ean13CheckDigit(first12: String): Char {
    val sum = first12.mapIndexed { i, c -> (c - '0') * (if (i % 2 == 0) 1 else 3) }.sum()
    return '0' + (10 - sum % 10) % 10
}

private val EAN_L = listOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
private val EAN_PARITY = listOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

private fun ean13(value: String): BooleanArray {
    val digits = value.map { it - '0' }
    val r = EAN_L.map { code -> code.map { if (it == '0') '1' else '0' }.joinToString("") }
    val g = r.map { it.reversed() }
    val left = (1..6).joinToString("") { i -> if (EAN_PARITY[digits[0]][i - 1] == 'L') EAN_L[digits[i]] else g[digits[i]] }
    val right = (7..12).joinToString("") { i -> r[digits[i]] }
    return ("101" + left + "01010" + right + "101").map { it == '1' }.toBooleanArray()
}

/** Bar and space widths of the 107 Code 128 symbols (values 0 to 102, then Start A, Start B, Start C). */
internal val CODE128 = listOf(
    "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
    "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
    "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
    "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
    "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
    "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
    "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
    "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
    "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
    "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
    "114131", "311141", "411131", "211412", "211214", "211232",
)
internal const val CODE128_STOP = "2331112"
private const val START_B = 104
private const val START_C = 105
private const val SWITCH_TO_B = 100

/** Digits only (2 or more) use code set C, which halves the width. Odd digit counts switch to B for the last one. */
private fun code128(value: String): BooleanArray {
    val digitsOnly = value.length >= 2 && value.all { it in '0'..'9' }
    val symbols = if (digitsOnly) {
        val pairs = value.length / 2
        val list = mutableListOf(START_C)
        for (i in 0 until pairs) list += value.substring(2 * i, 2 * i + 2).toInt()
        if (value.length % 2 == 1) list += listOf(SWITCH_TO_B, value.last().code - 32)
        list
    } else {
        listOf(START_B) + value.map { it.code - 32 }
    }
    val checksum = (symbols.first() + symbols.drop(1).mapIndexed { i, v -> (i + 1) * v }.sum()) % 103
    val widths = (symbols + checksum).joinToString("") { CODE128[it] } + CODE128_STOP
    val bars = mutableListOf<Boolean>()
    widths.forEachIndexed { i, w -> repeat(w - '0') { bars += i % 2 == 0 } }
    return bars.toBooleanArray()
}
```

### Layouts.kt

`Layouts.kt` · 151 Zeilen

```kotlin
package bayern.kickner.inktap.core

import kotnexlib.ResultOf2

/** Why a content cannot be drawn. The UI maps every case to one German sentence. */
sealed interface ContentError {
    /** A required field is empty. */
    data object Empty : ContentError

    /** The QR content does not fit with modules of at least [MIN_QR_MODULE_PX] pixels. */
    data object QrTooLong : ContentError

    /** The barcode does not fit with bars of at least one pixel. */
    data object BarcodeTooLong : ContentError

    /** Code 128 only allows printable ASCII characters. */
    data object BarcodeInvalidChars : ContentError

    /** The text does not fit even with the smallest font size. */
    data object TextTooLong : ContentError
}

/**
 * Renders text into a white box of [width] x [height] pixels, as large as it fits. Returns null if it does not
 * fit even at the smallest font size. Implemented with Android's `StaticLayout` in the app and with AWT in tests.
 */
fun interface TextRasterizer {
    /** See [TextRasterizer]. */
    fun rasterize(text: String, width: Int, height: Int): MonoImage?
}

/** Everything the user can put on the display. Each case is one mode in the UI. */
sealed interface Content {
    /** One QR code, centered. */
    data class Qr(val text: String) : Content

    /** QR code on the left, free text on the right. */
    data class QrWithNote(val text: String, val note: String) : Content

    /** QR code on the left, its own content as readable text on the right. */
    data class QrWithContent(val text: String) : Content

    /** Two QR codes next to each other. */
    data class TwoQr(val left: String, val right: String) : Content

    /** Only text, as large as possible. */
    data class Text(val text: String) : Content

    /** A barcode, optionally with its value printed below. */
    data class Barcode(val value: String, val showValue: Boolean = true) : Content

    /** A picture that is already scaled to the display size, see [fitInto]. */
    data class Picture(val image: GrayImage, val dither: Boolean = true) : Content

    /**
     * Calibration image: a 1 pixel frame on all four edges plus the word "OBEN" at the top. If one frame line is
     * missing on the real display, the visible area is offset, if the word is at the bottom, switch on rotate180.
     */
    data object TestPattern : Content
}

/** Outer margin in pixels for text and barcodes. */
private const val MARGIN = 4

/** Height of the value line below a barcode. */
private const val BARCODE_TEXT_HEIGHT = 22

/** Renders [content] into a [Display.WIDTH] x [Display.HEIGHT] image. */
fun render(content: Content, text: TextRasterizer): ResultOf2<MonoImage, ContentError> {
    val canvas = MonoImage(Display.WIDTH, Display.HEIGHT)
    val h = Display.HEIGHT
    val error: ContentError? = when (content) {
        is Content.Qr -> place(renderQr(content.text, h), canvas, (Display.WIDTH - h) / 2, 0)
        is Content.QrWithNote -> qrAndText(content.text, content.note, canvas, text)
        is Content.QrWithContent -> qrAndText(content.text, content.text, canvas, text)
        is Content.TwoQr -> {
            val half = Display.WIDTH / 2
            val size = minOf(half, h)
            place(renderQr(content.left, size), canvas, (half - size) / 2, (h - size) / 2)
                ?: place(renderQr(content.right, size), canvas, half + (half - size) / 2, (h - size) / 2)
        }
        is Content.Text -> textInto(content.text, canvas, text, MARGIN, MARGIN, Display.WIDTH - 2 * MARGIN, h - 2 * MARGIN)
        is Content.Barcode -> barcode(content, canvas, text)
        is Content.Picture -> {
            canvas.draw(content.image.toMonoImage(content.dither), 0, 0)
            null
        }
        is Content.TestPattern -> {
            canvas.fillRect(0, 0, Display.WIDTH, 1)
            canvas.fillRect(0, h - 1, Display.WIDTH, 1)
            canvas.fillRect(0, 0, 1, h)
            canvas.fillRect(Display.WIDTH - 1, 0, 1, h)
            textInto("OBEN", canvas, text, MARGIN, MARGIN, Display.WIDTH - 2 * MARGIN, 40)
        }
    }
    return if (error == null) ResultOf2.Success(canvas) else ResultOf2.Failure(error)
}

private fun place(result: ResultOf2<MonoImage, ContentError>, canvas: MonoImage, x: Int, y: Int): ContentError? =
    when (result) {
        is ResultOf2.Success -> {
            canvas.draw(result.value, x, y)
            null
        }
        is ResultOf2.Failure -> result.value
    }

private fun qrAndText(qr: String, note: String, canvas: MonoImage, text: TextRasterizer): ContentError? {
    val size = Display.HEIGHT
    val textX = size + MARGIN
    return place(renderQr(qr, size), canvas, 0, 0)
        ?: textInto(note, canvas, text, textX, MARGIN, Display.WIDTH - textX - MARGIN, size - 2 * MARGIN)
}

private fun textInto(value: String, canvas: MonoImage, text: TextRasterizer, x: Int, y: Int, w: Int, h: Int): ContentError? {
    if (value.isBlank()) return ContentError.Empty
    val image = text.rasterize(value.trim(), w, h) ?: return ContentError.TextTooLong
    canvas.draw(image, x + (w - image.width) / 2, y + (h - image.height) / 2)
    return null
}

private fun barcode(content: Content.Barcode, canvas: MonoImage, text: TextRasterizer): ContentError? {
    val modules = when (val r = encodeBarcode(content.value)) {
        is ResultOf2.Success -> r.value
        is ResultOf2.Failure -> return r.value
    }
    val total = modules.quietLeft + modules.bars.size + modules.quietRight
    val px = Display.WIDTH / total
    if (px < 1) return ContentError.BarcodeTooLong
    val barHeight = Display.HEIGHT - 2 * MARGIN - if (content.showValue) BARCODE_TEXT_HEIGHT else 0
    val left = (Display.WIDTH - total * px) / 2 + modules.quietLeft * px
    modules.bars.forEachIndexed { i, bar -> if (bar) canvas.fillRect(left + i * px, MARGIN, px, barHeight) }
    if (content.showValue.not()) return null
    val textTop = MARGIN + barHeight
    return textInto(content.value, canvas, text, MARGIN, textTop, Display.WIDTH - 2 * MARGIN, BARCODE_TEXT_HEIGHT)
}

/**
 * Largest value in [min]..[max] for which [fits] is true, found by binary search. Returns null if not even
 * [min] fits. [fits] must be monotonic (true up to some size, false above). Used to pick the font size.
 */
fun largestFitting(min: Int, max: Int, fits: (Int) -> Boolean): Int? {
    if (fits(min).not()) return null
    var low = min
    var high = max
    while (low < high) {
        val mid = (low + high + 1) / 2
        if (fits(mid)) low = mid else high = mid - 1
    }
    return low
}
```

### Pictures.kt

`Pictures.kt` · 65 Zeilen

```kotlin
package bayern.kickner.inktap.core

/**
 * A rectangle in pixels.
 *
 * @property x Left edge.
 * @property y Top edge.
 * @property w Width.
 * @property h Height.
 */
data class PixelRect(val x: Int, val y: Int, val w: Int, val h: Int)

/**
 * Where a picture of [srcW] x [srcH] goes on the display, keeping its aspect ratio.
 *
 * @param crop `true` fills the whole display and cuts off what sticks out (good for photos), `false` shows the
 * whole picture with white bars (good for logos and screenshots).
 * @return The target rectangle on the display. With [crop] it may start at negative coordinates.
 */
fun fitInto(srcW: Int, srcH: Int, crop: Boolean, dstW: Int = Display.WIDTH, dstH: Int = Display.HEIGHT): PixelRect {
    require(srcW > 0 && srcH > 0) { "Picture size must be positive" }
    val scaleX = dstW.toDouble() / srcW
    val scaleY = dstH.toDouble() / srcH
    val scale = if (crop) maxOf(scaleX, scaleY) else minOf(scaleX, scaleY)
    val w = Math.round(srcW * scale).toInt().coerceAtLeast(1)
    val h = Math.round(srcH * scale).toInt().coerceAtLeast(1)
    return PixelRect((dstW - w) / 2, (dstH - h) / 2, w, h)
}

/**
 * Converts to black and white.
 *
 * @param dither `true` uses Floyd-Steinberg error diffusion, so photos keep their shades. `false` is a plain
 * threshold at the middle gray, which keeps logos and text crisp.
 */
fun GrayImage.toMonoImage(dither: Boolean): MonoImage {
    val out = MonoImage(width, height)
    val work = luma.copyOf()
    for (y in 0 until height) for (x in 0 until width) {
        val old = work[y * width + x].coerceIn(0, 255)
        val black = old < 128
        out[x, y] = black
        if (dither.not()) continue
        val err = old - if (black) 0 else 255
        spread(work, x + 1, y, err * 7 / 16)
        spread(work, x - 1, y + 1, err * 3 / 16)
        spread(work, x, y + 1, err * 5 / 16)
        spread(work, x + 1, y + 1, err * 1 / 16)
    }
    return out
}

private fun GrayImage.spread(work: IntArray, x: Int, y: Int, amount: Int) {
    if (x in 0 until width && y in 0 until height) work[y * width + x] += amount
}

/** Perceived brightness (0 to 255) of an ARGB pixel, ITU-R BT.601 weights. Transparent counts as white. */
fun lumaOf(argb: Int): Int {
    val alpha = argb ushr 24 and 0xFF
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    val luma = (299 * r + 587 * g + 114 * b) / 1000
    return (luma * alpha + 255 * (255 - alpha)) / 255
}
```


## Anhang B: Tests (verifiziert)

Pfad jeweils `app/src/test/kotlin/bayern/kickner/inktap/core/`. Ergebnis der Prüfung: `OK (36 tests)`.

### TestSupport.kt

`TestSupport.kt` · 77 Zeilen

```kotlin
package bayern.kickner.inktap.core

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotnexlib.ResultOf2
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import kotlin.test.fail

/**
 * What the panel shows for a frame: the inverse of [FramePacker.pack], derived independently from the SDK
 * memory layout (portrait row r, column c is landscape pixel x = 249 - r, y = c, columns 122 to 127 invisible).
 */
fun displayedImage(frame: ByteArray): MonoImage {
    val image = MonoImage(Display.WIDTH, Display.HEIGHT)
    for (row in 0 until 250) for (col in 0 until 122) {
        val bit = frame[row * 16 + col / 8].toInt() and (0x80 ushr (col % 8))
        image[249 - row, col] = bit == 0
    }
    return image
}

/**
 * Decodes the first barcode or QR code ZXing finds, like a phone camera would: the image is scaled 3x and gets a
 * white border (the display case). Returns null if nothing is found.
 */
fun decode(image: MonoImage, x: Int = 0, y: Int = 0, w: Int = image.width, h: Int = image.height): String? {
    val scale = 3
    val border = 20
    val outW = w * scale + 2 * border
    val outH = h * scale + 2 * border
    val argb = IntArray(outW * outH) { 0xFFFFFFFF.toInt() }
    for (yy in 0 until h * scale) for (xx in 0 until w * scale) {
        if (image[x + xx / scale, y + yy / scale]) argb[(yy + border) * outW + xx + border] = 0xFF000000.toInt()
    }
    val bitmap = BinaryBitmap(HybridBinarizer(RGBLuminanceSource(outW, outH, argb)))
    val hints = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.CHARACTER_SET to "UTF-8")
    return runCatching { MultiFormatReader().decode(bitmap, hints).text }.getOrNull()
}

/** The whole chain the app uses: layout, frame, what the panel shows. */
fun renderToPanel(content: Content, rotate180: Boolean = false): MonoImage {
    val image = when (val r = render(content, AwtText)) {
        is ResultOf2.Success -> r.value
        is ResultOf2.Failure -> fail("Render failed: ${r.value}")
    }
    return displayedImage(FramePacker.pack(image, rotate180))
}

/** Test stand-in for the Android `StaticLayout` rasterizer: AWT, bold sans serif, largest size that fits. */
object AwtText : TextRasterizer {
    override fun rasterize(text: String, width: Int, height: Int): MonoImage? {
        val lines = text.lines()
        val probe = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY).createGraphics()
        val size = largestFitting(10, 80) { px ->
            val metrics = probe.getFontMetrics(Font(Font.SANS_SERIF, Font.BOLD, px))
            lines.maxOf { metrics.stringWidth(it) } <= width && metrics.height * lines.size <= height
        } ?: return null
        val img = BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY)
        val g = img.createGraphics()
        g.color = java.awt.Color.WHITE
        g.fillRect(0, 0, width, height)
        g.color = java.awt.Color.BLACK
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF)
        g.font = Font(Font.SANS_SERIF, Font.BOLD, size)
        val m = g.fontMetrics
        val top = (height - m.height * lines.size) / 2
        lines.forEachIndexed { i, line -> g.drawString(line, (width - m.stringWidth(line)) / 2, top + i * m.height + m.ascent) }
        val out = MonoImage(width, height)
        for (yy in 0 until height) for (xx in 0 until width) out[xx, yy] = img.getRGB(xx, yy) and 0xFFFFFF == 0
        return out
    }
}
```

### FramePackerTest.kt

`FramePackerTest.kt` · 86 Zeilen

```kotlin
package bayern.kickner.inktap.core

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FramePackerTest {

    /**
     * Literal re-implementation of the official SDK (`NFC.jar` 2.13 inch branch, read from its bytecode):
     * a 250 x 128 ARGB bitmap is rotated by 270 degrees with `Matrix.setRotate(270f)`, which maps (x, y) to
     * (y, 249 - x), then every row of 128 pixels is packed MSB first with `(pixel and 0xFF) > 128` meaning 1 (white).
     */
    private fun sdkPipeline(image: MonoImage): ByteArray {
        val w = 250
        val h = 128
        val landscape = IntArray(w * h) { i ->
            val black = image[i % w, i / w]
            if (black) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val portrait = IntArray(h * w)
        for (y in 0 until h) for (x in 0 until w) portrait[(w - 1 - x) * h + y] = landscape[y * w + x]
        val out = ByteArray(4000)
        for (row in 0 until 250) for (b in 0 until 16) {
            var data = 0
            for (bit in 0 until 8) {
                data = (data shl 1) and 0xFF
                val pixel = portrait[(b * 8 + bit) + row * 128] and 0xFF
                if (pixel > 128) data = data or 1
            }
            out[row * 16 + b] = data.toByte()
        }
        return out
    }

    private fun randomImage(seed: Int): MonoImage {
        val random = Random(seed)
        val image = MonoImage(Display.WIDTH, Display.HEIGHT)
        for (y in 0 until Display.HEIGHT) for (x in 0 until Display.WIDTH) image[x, y] = random.nextBoolean()
        return image
    }

    @Test
    fun matchesTheWaveshareSdkPipeline() {
        repeat(20) { seed ->
            val image = randomImage(seed)
            assertContentEquals(sdkPipeline(image), FramePacker.pack(image))
        }
    }

    @Test
    fun whiteImageIsAllFF() {
        val frame = FramePacker.pack(MonoImage(Display.WIDTH, Display.HEIGHT))
        assertEquals(4000, frame.size)
        assertTrue(frame.all { it == 0xFF.toByte() })
    }

    @Test
    fun topLeftPixelIsLastRowFirstBit() {
        val image = MonoImage(Display.WIDTH, Display.HEIGHT)
        image[0, 0] = true
        val frame = FramePacker.pack(image)
        assertEquals(0x7F.toByte(), frame[249 * 16])
        assertEquals(1, frame.count { it != 0xFF.toByte() })
    }

    @Test
    fun displayModelIsTheExactInverse() {
        repeat(5) { seed ->
            val image = randomImage(100 + seed)
            val shown = displayedImage(FramePacker.pack(image))
            for (y in 0 until Display.HEIGHT) for (x in 0 until Display.WIDTH) assertEquals(image[x, y], shown[x, y])
        }
    }

    @Test
    fun rotate180TurnsThePictureUpsideDown() {
        val image = MonoImage(Display.WIDTH, Display.HEIGHT)
        image[0, 0] = true
        val shown = displayedImage(FramePacker.pack(image, rotate180 = true))
        assertTrue(shown[249, 121])
        assertEquals(1, shown.blackCount())
    }
}
```

### EpdWriterTest.kt

`EpdWriterTest.kt` · 98 Zeilen

```kotlin
package bayern.kickner.inktap.core

import kotnexlib.ResultOfEmpty
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A simulated display. It answers `00 00` to everything, reports "refresh done" (`FF 00`) on the
 * [busyPolls]+1-th `CD 0A` and stores every row it receives, so the frame can be compared afterwards.
 */
private class FakeDisplay(
    val busyPolls: Int = 3,
    val failOn: (ByteArray, Int) -> ByteArray? = { _, _ -> null },
) {
    val sent = mutableListOf<ByteArray>()
    val received = ByteArray(4000)
    private var rows = 0
    private var polls = 0

    fun transceive(cmd: ByteArray): ByteArray {
        sent += cmd
        failOn(cmd, sent.size)?.let { return it }
        if (cmd[1] == 0x08.toByte()) {
            cmd.copyInto(received, rows * 16, 3, 19)
            rows++
        }
        if (cmd[1] == 0x0A.toByte()) {
            polls++
            return if (polls > busyPolls) byteArrayOf(-1, 0) else byteArrayOf(0, 0)
        }
        return byteArrayOf(0, 0)
    }
}

class EpdWriterTest {

    private val frame = ByteArray(4000) { (it * 7).toByte() }

    private fun hex(cmd: ByteArray) = cmd.joinToString(" ") { "%02X".format(it) }

    @Test
    fun sendsTheSdkSequence() {
        val display = FakeDisplay()
        val sleeps = mutableListOf<Long>()
        val progress = mutableListOf<Float>()
        val result = EpdWriter(display::transceive, sleep = { sleeps += it }).write(frame) { progress += it }

        assertEquals(ResultOfEmpty.Success, result)
        val heads = display.sent.map { hex(it.copyOf(minOf(it.size, 3))) }
        assertEquals(listOf("CD 0D", "CD 00 04", "CD 01", "CD 02", "CD 03", "CD 05", "CD 06", "CD 07 00"), heads.take(8))
        assertTrue(heads.drop(8).take(250).all { it == "CD 08 10" })
        assertTrue(display.sent.drop(8).take(250).all { it.size == 19 })
        assertEquals(listOf("CD 18", "CD 09", "CD 0A", "CD 0A", "CD 0A", "CD 0A", "CD 04"), heads.drop(258))
        assertContentEquals(frame, display.received)
        assertEquals(listOf(50L, 20L, 20L, 20L, 20L, 10L), sleeps.take(6))
        assertEquals(1f, progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun retriesARejectedCommand() {
        val display = FakeDisplay(failOn = { _, n -> if (n == 20 || n == 21) byteArrayOf(1, 0) else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame)
        assertEquals(ResultOfEmpty.Success, result)
        assertContentEquals(frame, display.received)
        assertEquals(8 + 250 + 7 + 2, display.sent.size)
    }

    @Test
    fun givesUpAfterTenRejections() {
        val display = FakeDisplay(failOn = { cmd, _ -> if (cmd[1] == 0x03.toByte()) byteArrayOf(1, 2) else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame)
        val error = assertIs<ResultOfEmpty.Failure<WriteError>>(result).value
        assertEquals(WriteError.Rejected("power on", "0102"), error)
        assertEquals(10, display.sent.count { it[1] == 0x03.toByte() })
    }

    @Test
    fun lostTagIsReportedNotThrown() {
        val display = FakeDisplay(failOn = { _, n -> if (n == 100) throw IOException("Tag was lost.") else null })
        val result = EpdWriter(display::transceive, sleep = {}).write(frame)
        val error = assertIs<WriteError.ConnectionLost>(assertIs<ResultOfEmpty.Failure<WriteError>>(result).value)
        assertEquals("data row 91", error.step)
    }

    @Test
    fun refreshTimeoutAfterTenSeconds() {
        var now = 0L
        val display = FakeDisplay(busyPolls = Int.MAX_VALUE)
        val writer = EpdWriter(display::transceive, sleep = { now += it }, clock = { now })
        val result = writer.write(frame)
        assertIs<WriteError.RefreshTimeout>(assertIs<ResultOfEmpty.Failure<WriteError>>(result).value)
    }
}
```

### LayoutTest.kt

`LayoutTest.kt` · 148 Zeilen

```kotlin
package bayern.kickner.inktap.core

import kotnexlib.ResultOf2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every layout goes through the full chain (layout, frame, panel model) and is then read back with ZXing,
 * an independent decoder. If ZXing reads it from 250 x 122 pixels, a phone camera reads it from the panel.
 */
class LayoutTest {

    private val url = "https://www.kickner.bayern/inktap?id=42"

    @Test
    fun singleQr() = assertEquals(url, decode(renderToPanel(Content.Qr(url))))

    @Test
    fun singleQrWithUmlautsAndEmoji() {
        val text = "Grüße aus Bayern! ÄÖÜß 😀"
        assertEquals(text, decode(renderToPanel(Content.Qr(text))))
    }

    @Test
    fun wifiQr() {
        // The WIFI payload format itself is defined with semicolons.
        val wifi = "WIFI:T:WPA;S:Gast;P:sehr-geheim-123;;"
        assertEquals(wifi, decode(renderToPanel(Content.Qr(wifi))))
    }

    @Test
    fun qrWithNoteHasTextOnTheRight() {
        val panel = renderToPanel(Content.QrWithNote(url, "WLAN\nGast"))
        assertEquals(url, decode(panel, 0, 0, 122, 122))
        assertTrue(blackIn(panel, 126, 0, 124, 122) > 200)
    }

    @Test
    fun qrWithContentShowsTheContent() {
        val panel = renderToPanel(Content.QrWithContent("ABC-123"))
        assertEquals("ABC-123", decode(panel, 0, 0, 122, 122))
        assertTrue(blackIn(panel, 126, 0, 124, 122) > 100)
    }

    @Test
    fun twoQrCodesAreBothReadable() {
        val panel = renderToPanel(Content.TwoQr("links", url))
        assertEquals("links", decode(panel, 0, 0, 125, 122))
        assertEquals(url, decode(panel, 125, 0, 125, 122))
    }

    @Test
    fun longestQrThatStillFits() {
        val text = "x".repeat(213)
        assertEquals(text, decode(renderToPanel(Content.Qr(text))))
        assertEquals(ContentError.QrTooLong, failure(Content.Qr("x".repeat(214))))
    }

    @Test
    fun twoQrCodesHaveTheSameLimit() {
        val text = "y".repeat(213)
        val panel = renderToPanel(Content.TwoQr(text, text))
        assertEquals(text, decode(panel, 0, 0, 125, 122))
    }

    @Test
    fun emptyInputIsAnError() {
        assertEquals(ContentError.Empty, failure(Content.Qr("")))
        assertEquals(ContentError.Empty, failure(Content.QrWithNote(url, "   ")))
        assertEquals(ContentError.Empty, failure(Content.Text("")))
        assertEquals(ContentError.Empty, failure(Content.Barcode("")))
    }

    @Test
    fun textIsDrawnLargeAndCentered() {
        val panel = renderToPanel(Content.Text("Hallo"))
        assertTrue(panel.blackCount() > 2000)
        assertEquals(ContentError.TextTooLong, failure(Content.Text("Wort ".repeat(200))))
    }

    @Test
    fun code128Text() = assertEquals("Kunde-4711", decode(renderToPanel(Content.Barcode("Kunde-4711"))))

    @Test
    fun code128EvenAndOddDigits() {
        assertEquals("12345678", decode(renderToPanel(Content.Barcode("12345678", showValue = false))))
        assertEquals("1234567", decode(renderToPanel(Content.Barcode("1234567", showValue = false))))
    }

    @Test
    fun ean13IsDetectedAndReadable() {
        val ean = "4006381333931"
        assertEquals(BarcodeFormat.EAN_13, assertIs<ResultOf2.Success<BarcodeModules>>(encodeBarcode(ean)).value.format)
        assertEquals(ean, decode(renderToPanel(Content.Barcode(ean))))
    }

    @Test
    fun thirteenDigitsWithWrongCheckDigitBecomeCode128() {
        val value = "4006381333932"
        assertEquals(BarcodeFormat.CODE_128, assertIs<ResultOf2.Success<BarcodeModules>>(encodeBarcode(value)).value.format)
        assertEquals(value, decode(renderToPanel(Content.Barcode(value))))
    }

    @Test
    fun barcodeLimits() {
        assertEquals(ContentError.BarcodeInvalidChars, failure(Content.Barcode("Grüße")))
        val longest = "A".repeat(17)
        assertEquals(longest, decode(renderToPanel(Content.Barcode(longest))))
        assertEquals(ContentError.BarcodeTooLong, failure(Content.Barcode(longest + "A")))
        val digits = "1234567890".repeat(3) + "1234"
        assertEquals(digits, decode(renderToPanel(Content.Barcode(digits))))
        assertEquals(ContentError.BarcodeTooLong, failure(Content.Barcode(digits + "56")))
    }

    @Test
    fun code128PatternsMatchZxing() {
        val field = Class.forName("com.google.zxing.oned.Code128Reader").getDeclaredField("CODE_PATTERNS")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val zxing = (field.get(null) as Array<IntArray>).map { it.joinToString("") }
        assertEquals(zxing.take(106), CODE128)
        assertEquals(CODE128_STOP, zxing[106])
    }

    @Test
    fun rotate180IsStillReadable() = assertEquals(url, decode(renderToPanel(Content.Qr(url), rotate180 = true)))

    @Test
    fun largestFittingFindsTheBoundary() {
        assertEquals(37, largestFitting(10, 80) { it <= 37 })
        assertEquals(80, largestFitting(10, 80) { true })
        assertNull(largestFitting(10, 80) { false })
        assertNotNull(largestFitting(10, 10) { true })
    }

    private fun failure(content: Content): ContentError =
        assertIs<ResultOf2.Failure<ContentError>>(render(content, AwtText)).value

    private fun blackIn(img: MonoImage, x: Int, y: Int, w: Int, h: Int): Int {
        var n = 0
        for (yy in y until y + h) for (xx in x until x + w) if (img[xx, yy]) n++
        return n
    }
}
```

### PictureTest.kt

`PictureTest.kt` · 66 Zeilen

```kotlin
package bayern.kickner.inktap.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PictureTest {

    @Test
    fun cropFillsTheDisplay() {
        // A 4:3 photo is wider than tall but less wide than the display, so it is cut at top and bottom.
        assertEquals(PixelRect(0, -33, 250, 188), fitInto(4000, 3000, crop = true))
    }

    @Test
    fun fitShowsEverything() = assertEquals(PixelRect(84, 0, 81, 122), fitInto(3000, 4500, crop = false))

    @Test
    fun ditherKeepsMiddleGray() {
        val gray = GrayImage(Display.WIDTH, Display.HEIGHT, IntArray(Display.WIDTH * Display.HEIGHT) { 128 })
        val share = gray.toMonoImage(dither = true).blackCount().toDouble() / (Display.WIDTH * Display.HEIGHT)
        assertTrue(share in 0.45..0.55, "black share was $share")
    }

    @Test
    fun thresholdIsCrisp() {
        val luma = IntArray(Display.WIDTH * Display.HEIGHT) { if (it % Display.WIDTH < 125) 30 else 220 }
        val mono = GrayImage(Display.WIDTH, Display.HEIGHT, luma).toMonoImage(dither = false)
        assertEquals(125 * Display.HEIGHT, mono.blackCount())
    }

    @Test
    fun lumaOfKnownColors() {
        assertEquals(0, lumaOf(0xFF000000.toInt()))
        assertEquals(255, lumaOf(0xFFFFFFFF.toInt()))
        assertEquals(76, lumaOf(0xFFFF0000.toInt()))
        assertEquals(255, lumaOf(0x00000000))
    }

    @Test
    fun previewColors() {
        val image = MonoImage(2, 1)
        image[0, 0] = true
        val argb = image.toArgb(ink = 1, paper = 2)
        assertEquals(listOf(1, 2), argb.toList())
    }

    @Test
    fun testPatternHasAllFourEdgesAndTextAtTheTop() {
        val panel = renderToPanel(Content.TestPattern)
        for (x in 0 until Display.WIDTH) assertTrue(panel[x, 0] && panel[x, Display.HEIGHT - 1])
        for (y in 0 until Display.HEIGHT) assertTrue(panel[0, y] && panel[Display.WIDTH - 1, y])
        var top = 0
        var bottom = 0
        for (y in 2 until 60) for (x in 2 until Display.WIDTH - 2) if (panel[x, y]) top++
        for (y in 62 until Display.HEIGHT - 2) for (x in 2 until Display.WIDTH - 2) if (panel[x, y]) bottom++
        assertTrue(top > 300 && bottom == 0, "top=$top bottom=$bottom")
    }

    @Test
    fun pictureLayoutUsesTheWholeDisplay() {
        val luma = IntArray(Display.WIDTH * Display.HEIGHT) { 0 }
        val panel = renderToPanel(Content.Picture(GrayImage(Display.WIDTH, Display.HEIGHT, luma), dither = false))
        assertEquals(Display.WIDTH * Display.HEIGHT, panel.blackCount())
    }
}
```


## Anhang C: Android-Plattformcode

Gegen Signatur-Stubs der benutzten Android-APIs kompiliert (kein echtes `android.jar` verfügbar). Task 5 baut
es gegen das echte SDK.

### AndroidText.kt

`app/src/main/kotlin/bayern/kickner/inktap/platform/AndroidText.kt` · 65 Zeilen

```kotlin
package bayern.kickner.inktap.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import bayern.kickner.inktap.core.MonoImage
import bayern.kickner.inktap.core.TextRasterizer
import bayern.kickner.inktap.core.largestFitting
import bayern.kickner.inktap.core.lumaOf

/** Smallest font size in pixels that is still readable on the panel. */
private const val MIN_FONT_PX = 10

/** Largest font size in pixels, a single short word fills the display at about this size. */
private const val MAX_FONT_PX = 96

/**
 * [TextRasterizer] on top of `StaticLayout`: bold, centered, word wrap, as large as it fits. Anti-aliasing is
 * off, because the panel only knows black and white and grey edge pixels would turn into ragged dots.
 */
object AndroidText : TextRasterizer {

    override fun rasterize(text: String, width: Int, height: Int): MonoImage? {
        val paint = TextPaint().apply {
            isAntiAlias = false
            color = Color.BLACK
            typeface = Typeface.DEFAULT_BOLD
        }
        // Whole words first. Long URLs have no spaces, they may be broken anywhere as a fallback.
        val size = fittingSize(text, paint, width, height, wholeWords = true)
            ?: fittingSize(text, paint, width, height, wholeWords = false)
            ?: return null

        paint.textSize = size.toFloat()
        val layout = layout(text, paint, width)
        val bitmap = Bitmap.createBitmap(width, layout.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        layout.draw(canvas)
        val argb = IntArray(width * layout.height)
        bitmap.getPixels(argb, 0, width, 0, 0, width, layout.height)
        val image = MonoImage(width, layout.height)
        argb.forEachIndexed { i, pixel -> image[i % width, i / width] = lumaOf(pixel) < 128 }
        return image
    }

    private fun fittingSize(text: String, paint: TextPaint, width: Int, height: Int, wholeWords: Boolean): Int? {
        val words = text.split(' ', '\n').filter { it.isNotEmpty() }
        return largestFitting(MIN_FONT_PX, MAX_FONT_PX) { px ->
            paint.textSize = px.toFloat()
            val wordsFit = wholeWords.not() || words.all { paint.measureText(it) <= width }
            wordsFit && layout(text, paint, width).height <= height
        }
    }

    private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false)
            .build()
}
```

### PictureLoader.kt

`app/src/main/kotlin/bayern/kickner/inktap/platform/PictureLoader.kt` · 44 Zeilen

```kotlin
package bayern.kickner.inktap.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import bayern.kickner.inktap.core.Display
import bayern.kickner.inktap.core.GrayImage
import bayern.kickner.inktap.core.fitInto
import bayern.kickner.inktap.core.lumaOf

/** Longer edge of the decoded picture. Enough for 250 x 122 and keeps a 50 megapixel photo out of memory. */
private const val DECODE_EDGE_PX = 1000

/**
 * Decodes the picture behind [uri] (from the Photo Picker) into a small software bitmap. Blocking, call it
 * from `Dispatchers.IO`. Returns a failed [Result] for broken or unsupported files.
 */
fun decodePicture(context: Context, uri: Uri): Result<Bitmap> = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        // Software memory, otherwise getPixels fails on hardware bitmaps.
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = maxOf(info.size.width, info.size.height)
        decoder.setTargetSampleSize(maxOf(1, longest / DECODE_EDGE_PX))
    }
}

/** Scales [picture] onto a white 250 x 122 canvas, see [fitInto] for [crop], and returns its brightness. */
fun toDisplayGray(picture: Bitmap, crop: Boolean): GrayImage {
    val target = fitInto(picture.width, picture.height, crop)
    val canvasBitmap = Bitmap.createBitmap(Display.WIDTH, Display.HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(canvasBitmap)
    canvas.drawColor(Color.WHITE)
    val dst = Rect(target.x, target.y, target.x + target.w, target.y + target.h)
    canvas.drawBitmap(picture, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    val argb = IntArray(Display.WIDTH * Display.HEIGHT)
    canvasBitmap.getPixels(argb, 0, Display.WIDTH, 0, 0, Display.WIDTH, Display.HEIGHT)
    return GrayImage(Display.WIDTH, Display.HEIGHT, IntArray(argb.size) { lumaOf(argb[it]) })
}
```

### TagWriter.kt

`app/src/main/kotlin/bayern/kickner/inktap/nfc/TagWriter.kt` · 28 Zeilen

```kotlin
package bayern.kickner.inktap.nfc

import android.nfc.Tag
import android.nfc.tech.NfcA
import bayern.kickner.inktap.core.EpdWriter
import bayern.kickner.inktap.core.WriteError
import kotnexlib.ResultOfEmpty

/**
 * Transceive timeout in milliseconds. The SDK uses 700, the open source NFC E-Paper Writer app raised it to
 * 1200 because the display answers slowly while it is powered only by the phone's field.
 */
private const val NFC_TIMEOUT_MS = 1200

/**
 * Connects to [tag] and writes one frame from `FramePacker.pack`. Blocking for about 5 seconds, so call it
 * from `Dispatchers.IO`, never from the main thread.
 */
fun writeToTag(tag: Tag, frame: ByteArray, onProgress: (Float) -> Unit): ResultOfEmpty<WriteError> {
    val nfcA = NfcA.get(tag) ?: return ResultOfEmpty.Failure(WriteError.ConnectionLost("no NfcA", null))
    return runCatching {
        nfcA.use {
            it.connect()
            it.timeout = NFC_TIMEOUT_MS
            EpdWriter(it::transceive).write(frame, onProgress)
        }
    }.getOrElse { ResultOfEmpty.Failure(WriteError.ConnectionLost("connect", it)) }
}
```


## Anhang D: Protokoll-Referenz

Alle Befehle mit Präfix `0xCD`. Antwort `00 00` = OK, sonst bis zu 10 Wiederholungen.

| # | Bytes | Bedeutung (Waveshare) | Pause danach |
|---|---|---|---|
| 1 | `CD 0D` | Init | keine |
| 2 | `CD 00 04` | Panel wählen (4 = 2.13") und Reset | 50 ms |
| 3 | `CD 01` | Normal mode | 20 ms |
| 4 | `CD 02` | Config 1 | 20 ms |
| 5 | `CD 03` | Power on | 20 ms |
| 6 | `CD 05` | Config 2 | 20 ms |
| 7 | `CD 06` | Load to main | 10 ms |
| 8 | `CD 07 00` | Data preparation | keine |
| 9 | `CD 08 10` + 16 Byte, 250 Mal | Bilddaten, Zeile 0 bis 249 | je 2 ms |
| 10 | `CD 18` | Power on 2 | 200 ms |
| 11 | `CD 09` | Refresh | 300 ms |
| 12 | `CD 0A`, wiederholt | Fertig? `FF 00` = ja | 100 ms zwischen Abfragen, max. 10 s |
| 13 | `CD 04` | Power off | keine |

Abweichung vom SDK: Das SDK fragt höchstens 100 Mal im Abstand von 25 ms (also mindestens 2,5 s) ab, Proxmark3 bis zu
51 Mal mit 400 ms (etwa 20 s). InkTap nimmt dazwischen 10 s mit 100 ms Abstand.
