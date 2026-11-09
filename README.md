# Appothek

A collection of small Android helpers for everyday use, in one app. The name is a pun on app, Mediathek and
Apotheke: a shelf of little remedies. New ideas become a new tool in the Appothek instead of a new app in the store.

No internet permission, no tracking. Inputs stay on the device.

## Tools

| Tool | What it does |
|---|---|
| **Buchstabierer** | Spells words with the German spelling tables (DIN 5009 old and 2022), the international alphabet, random nouns, a fun "Robert Loggia" table or your own table. Includes a quiz, history and favourites. Formerly its own app in the Play Store. |
| **InkTap** | Writes QR codes, text, barcodes and pictures to the passive Waveshare NFC-powered e-paper displays. Hold the phone to the display, done. Needs NFC. |

Every tool can get its own launcher icon (long press on its card, or the settings sheet). It then behaves like its
own app: own entry in the recent apps, back closes it.

## Project layout

```
app/                  Appothek shell: start screen, settings sheet, one activity and launcher alias per tool, shortcuts
core/                 Tool interface, theme
tools/buchstabierer/  Buchstabierer, plus wordlist/ (script and source of the random-word list)
tools/inktap/         InkTap, its Android-free core/ package is covered by JVM tests
build-logic/          Gradle convention plugins (appothek.android.library, appothek.tool)
docs/superpowers/     Design and implementation plan
```

Tools depend only on `:core`, never on each other. Code two tools share goes to `:core` or to a module under
`shared/`.

### Adding a tool

1. Create `tools/<name>/build.gradle.kts` with `plugins { id("appothek.tool") }` and a namespace, and include it in
   `settings.gradle.kts`.
2. Implement `bayern.kickner.appothek.core.Tool` (id, name, one-line description, colours, motif, availability,
   `Content`). Prefix all string resources with the tool name, `ResourceNamesTest` fails on clashes.
3. Append it to `tools` in `app/src/main/kotlin/bayern/kickner/appothek/Tools.kt` (append only, cards never move).
4. Add its activity (own `taskAffinity`), its disabled launcher alias and its launcher icon to the app manifest,
   a `ToolActivity` subclass in `app/.../tool/ToolActivities.kt` and the class names in `LauncherEntries`
   and the icon in `Shortcuts`. `LauncherEntriesTest` checks the manifest.

## Build

Requirements: Android SDK with platform 37, JDK 17 or newer. Gradle provisions the daemon JDK (Corretto 25) itself.

```bash
./gradlew test assembleDebug
python3 -m unittest tools/buchstabierer/wordlist/test_build_wordlist.py
```

## Releases

Publishing a GitHub release with a tag like `v1.2.3` runs `.github/workflows/release.yml`. It builds the signed
release APK and AAB with `versionName` from the tag and attaches `Appothek-1.2.3.apk` and `Appothek-1.2.3.aab`.
The AAB is uploaded to the Play Console by hand.

The app ID is `bayern.kickner.buchstabierer` and the key is the one of the former Buchstabierer app, so the Play
Store app updates to the Appothek and keeps its users and data. Never change either.

### Signing secrets

Create an environment `release` (Settings, Environments) and limit it under "Deployment branches and tags" to tags
matching `v*`, optionally with yourself as required reviewer. Add these environment secrets:

| Secret | Content |
|---|---|
| `APPOTHEK_KEYSTORE_BASE64` | The release keystore, base64 encoded: `base64 -w0 buchstabierer.jks` |
| `APPOTHEK_KEYSTORE_PASSWORD` | Keystore password |
| `APPOTHEK_KEY_ALIAS` | Key alias |
| `APPOTHEK_KEY_PASSWORD` | Key password |

Keep an offline backup of the keystore: without it, no further updates are possible. All actions in the workflows
are pinned to commit SHAs.

Before the first release, compare the SHA-256 fingerprint of the key (`keytool -list -v -keystore buchstabierer.jks`)
with the "App signing key certificate" in the Play Console. Only if they match can the GitHub APK update a Play
Store install and the other way round.

## Supported InkTap displays

| Display | Resolution | Status |
|---|---|---|
| [2.13 inch](https://www.waveshare.com/wiki/2.13inch_NFC-Powered_e-Paper_Module) | 250 × 122 | Tested |
| [4.2 inch](https://www.waveshare.com/wiki/4.2inch_NFC-Powered_e-Paper_Module) | 400 × 300 | Protocol from the Waveshare SDK and Proxmark3, hardware test pending |

All models identify themselves the same way, so the app cannot detect the model. Pick it with the switch in
the top bar. On a new display, send the test pattern first: the frame must be visible on all four edges and
"OBEN" must be at the top, otherwise switch on "Rotate by 180°".

### Tested devices

| Phone | Display | Transfer time | Notes |
|---|---|---|---|
| Google Pixel 10 Pro | 2.13 inch | about 6 s | Phone held upright (portrait) on the display |

## Credits and licences

The Appothek is licensed under the [Apache License 2.0](LICENSE).

- Random words: [Free German Dictionary](https://sourceforge.net/projects/germandict/) by Jan Schreiber, Public
  Domain, filtered as described in `tools/buchstabierer/wordlist/SOURCE.md`.
- The InkTap NFC protocol was reconstructed from the official Waveshare SDK and the
  [Proxmark3](https://github.com/RfidResearchGroup/proxmark3) implementation (`cmdhfwaveshare.c`).
- QR encoding uses [qrcode-kotlin](https://github.com/g0dkar/qrcode-kotlin) (MIT).
