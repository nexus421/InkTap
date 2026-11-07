# InkTap

Android app that writes QR codes, text, barcodes and pictures to the passive
[Waveshare 2.13 inch NFC-powered e-paper](https://www.waveshare.com/wiki/2.13inch_NFC-Powered_e-Paper_Module)
over NFC. Hold the phone to the display, done. The display has no battery, power and data come from the phone.

## Features

- Seven layouts: QR, QR with note, QR with its content, two QR codes, text, barcode (EAN-13 or Code 128), picture
- Live preview with exactly the pixels the display will show
- Writes automatically when the phone touches the display, with progress, vibration and clear messages
- Test pattern and "rotate 180°" switch for calibration
- No internet permission, no tracking, inputs stay on the device and are excluded from backups

## Build

Requirements: Android SDK with platform 37, JDK 17 or newer. Gradle provisions the daemon JDK (Corretto 25) itself.

```bash
./gradlew test assembleDebug
```

The protocol, layouts and encoders live in `app/src/main/kotlin/bayern/kickner/inktap/core` without Android
dependencies and are covered by JVM unit tests. ZXing is used in the tests only, as an independent decoder.

## Releases

Publishing a GitHub release with a tag like `v1.2.3` runs `.github/workflows/release.yml`. It builds a signed
release APK with `versionName` taken from the tag and attaches it to the release as `InkTap-1.2.3.apk`.

The workflow needs these repository secrets (Settings, Secrets and variables, Actions):

| Secret | Content |
|---|---|
| `INKTAP_KEYSTORE_BASE64` | The release keystore, base64 encoded: `base64 -w0 inktap.jks` |
| `INKTAP_KEYSTORE_PASSWORD` | Keystore password |
| `INKTAP_KEY_ALIAS` | Key alias |
| `INKTAP_KEY_PASSWORD` | Key password |

Create a keystore once with `keytool -genkeypair -v -keystore inktap.jks -alias inktap -keyalg RSA -keysize 4096 -validity 10000`.
Keep it outside the repository and back it up: without it, later versions cannot be installed as updates.

## Tested devices

| Phone | Transfer time | Orientation |
|---|---|---|
| (pending) | | |

## Credits

The NFC protocol was reconstructed from the official Waveshare SDK and the
[Proxmark3](https://github.com/RfidResearchGroup/proxmark3) implementation (`cmdhfwaveshare.c`).
QR encoding uses [qrcode-kotlin](https://github.com/g0dkar/qrcode-kotlin).
