# FreeLift5 Development

## Requirements

- Android Studio with JDK 21
- Android SDK Platform 36
- Android SDK Build Tools 36.1
- An API 36 emulator or Android 9+ device

## Local Build

From the repository root:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
./gradlew connectedDebugAndroidTest
```

The debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The debug APK is signed with Android's development key and can be sideloaded for
testing. Production release signing and tagged GitHub Releases are documented in
[RELEASING.md](RELEASING.md).

## Engineering Docs

- [Architecture](ARCHITECTURE.md)
- [Lessons learned](LESSONS.md)

## Verification

The implementation is covered by 33 local unit tests and 13 instrumented tests.
The instrumented test matrix targets:

- Android 9 / API 28
- Android 16 / API 36

Run either matrix entry with `./scripts/test-avd.sh <avd-name>`.

Device coverage includes Room migration, routine and accessory progression,
partial-workout recovery, onboarding and navigation, active-session persistence,
timer persistence through activity recreation and backgrounding, and a
foreground timer with the screen off while notification permission is denied.
