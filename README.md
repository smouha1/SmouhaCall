# Smouha Call (Android)

Companion app for **Smouha Papper** Chrome extension: LAN bridge for Call / WhatsApp from the dashboard.

- **applicationId:** `com.smouha.call`
- **versionName:** 1.3.2 (`versionCode` 5)
- **minSdk:** 21 · **targetSdk:** 34

## Build on GitHub Actions (recommended)

1. Create a new empty repo on GitHub.
2. Upload this folder as the repo root (or `git push`).
3. Open **Actions** → **Build APK** → **Run workflow**.
4. When finished, download the artifact **SmouhaCall-apk**.

## Build on your machine

Requirements: **JDK 17**, Android SDK (API 34 + build-tools).

```bash
cp local.properties.example local.properties
# edit sdk.dir=...

# option A
./BUILD-APK.sh

# option B
gradle :app:assembleRelease
# APK: app/build/outputs/apk/release/
```

Signing uses a debug keystore under `/tmp/smouha-keys/debug.keystore` (created automatically by `BUILD-APK.sh` / CI). For Play Store distribution, replace with your own release keystore in `app/build.gradle.kts`.

## Pairing

See `PROTOCOL.md` for the HTTP protocol between the extension and the phone (port **8765**).
