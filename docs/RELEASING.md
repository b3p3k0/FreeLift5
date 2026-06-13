# Releasing FreeLift5

Production signing keys are intentionally not stored in this repository.

## Create A Key

Create and protect a long-lived signing key once:

```bash
keytool -genkeypair -v \
  -keystore freelift5-release.jks \
  -alias freelift5 \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Keep the keystore and passwords in a password manager and offline backup. Losing
the key prevents publishing upgrades under the same Android application ID.

## Local Signed Build

Set these environment variables:

```text
FREELIFT5_KEYSTORE_PATH=/absolute/path/freelift5-release.jks
FREELIFT5_KEYSTORE_PASSWORD=...
FREELIFT5_KEY_ALIAS=freelift5
FREELIFT5_KEY_PASSWORD=...
```

Then run:

```bash
./gradlew clean testDebugUnitTest lintDebug assembleRelease
sha256sum app/build/outputs/apk/release/app-release.apk
```

Without all four variables, Gradle still produces an unsigned release APK for
build verification. The debug APK remains signed and sideloadable for
development.

## GitHub Release

Configure these Actions secrets:

- `ANDROID_SIGNING_KEY_BASE64`
- `ANDROID_SIGNING_STORE_PASSWORD`
- `ANDROID_SIGNING_KEY_ALIAS`
- `ANDROID_SIGNING_KEY_PASSWORD`

Create `ANDROID_SIGNING_KEY_BASE64` with:

```bash
base64 -w 0 freelift5-release.jks
```

Push a tag such as `v0.1.0`. The release workflow runs tests and lint, builds a
signed universal APK, generates its SHA-256 checksum, and attaches both files to
the GitHub Release.
