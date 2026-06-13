# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

FreeLift5 is a private-by-design, offline Android tracker for the 5x5 barbell routine (Kotlin, Jetpack Compose, single `app` module). `docs/ARCHITECTURE.md` holds the data-flow overview.

On the original workstation only (gitignored, absent from clones): `PLAN.md` is the approved product contract and `DECISIONS.md` is the decision ledger — when present, append new decisions there so they survive context loss.

## Hard Constraints (Privacy Boundary)

These are product requirements, not omissions. Do not add:

- `INTERNET` or any network permission/dependency
- Exact-alarm, account, health-sensor, or analytics permissions
- Android automatic cloud backup (explicitly disabled)
- Telemetry, ads, accounts, or a data-import feature

The only runtime permission is Android 13+ notification access, requested after the user opts into timer alerts or reminders. Data leaves the device only via user-initiated CSV/ZIP export through the Android document picker or share sheet.

## Commands

Requires JDK 21 (Android Studio's bundled JBR works; scripts auto-locate it), SDK Platform 36, Build Tools 36.1.

```bash
# Host-side gate (unit tests, lint, build) — matches CI
./gradlew testDebugUnitTest lintDebug assembleDebug

# Single unit test class
./gradlew testDebugUnitTest --tests "org.freelift5.app.domain.ProgressionEngineTest"

# Instrumented tests (needs a running emulator/device)
./gradlew connectedDebugAndroidTest

# Instrumented tests on a managed headless AVD (starts/stops the emulator;
# fails if another device is already connected)
./scripts/test-avd.sh FreeLift5_API28
./scripts/test-avd.sh FreeLift5_API36

# One-time Linux workstation setup (SDK packages, licenses, sdk.dir, AVDs)
./scripts/bootstrap-android-linux.sh
```

Debug APK output: `app/build/outputs/apk/debug/app-debug.apk`. Release builds are unsigned unless `FREELIFT5_KEYSTORE_*` env vars are set (see `docs/RELEASING.md`); keep signing material out of the repo.

Instrumented tests must pass on both API 28 (min) and API 36 (target) before a change to data, timer, or progression code is considered verified.

## Architecture

Single-module app under `app/src/main/java/org/freelift5/app/`:

- `domain/` — pure Kotlin, no Android imports: `BuiltInPrograms` (the data-driven program registry and exercise catalog) and `Program` (`ProgramDefinition`/`SlotDef`/`SetScheme`/`ProgressionPolicy`), `ProgressionEngine` (increments, three-failure deload), `WeightMath`, `WarmupCalculator`, `PlateCalculator`. All business math lives here so it's unit-testable on the JVM. (`RoutineEngine` survives only as the base-program exercise definitions used by onboarding.)
- `data/` — Room (`FreeLiftDatabase`, `FreeLiftDao`, entities/relations), `FreeLiftRepository` (the single write path, owns transactions), `SettingsStore` (DataStore preferences), `AppContainer` (manual DI — no Hilt/Koin; constructed in `FreeLiftApplication`).
- `ui/` — Compose. One `AppViewModel` (an `AndroidViewModel` reaching `AppContainer` through the Application) exposes a single combined `AppUiState` flow; screens under `ui/workout`, `ui/progress`, `ui/settings`, `ui/program`, `ui/guides`, `ui/onboarding`. Three primary tabs: Workout, Progress, Settings; history lives inside Progress, program config inside Workout.
- `timer/` — `TimerStateStore` persists an absolute deadline in private SharedPreferences so the rest timer survives rotation, backgrounding, and process death; `RestTimerService` is the optional foreground service.
- `reminders/` — opt-in, approximate WorkManager reminders (never exact alarms).
- `export/` — versioned CSV and ZIP creation.

### Persistence rules that aren't obvious from one file

- **Weights are integer grams (`Long`) everywhere internally.** Conversion to lb/kg happens only at input, display, and export boundaries via `WeightMath`. Never store or compute progression in display units.
- **`ExerciseSessionEntity` is an immutable snapshot** of name, tracking mode, prescription, load, increments, and timers taken when a workout starts. Editing the program must never rewrite workout history.
- **Core progression is keyed by core slot, not exercise.** The shared Squat slot is referenced by both workout A and B until the user explicitly splits it; replacing an exercise changes the slot's assignment but preserves the slot's program state.
- **Every saved set is written to Room immediately** — this is what makes partial workouts and active-session recovery work. A partial workout still advances the program's day sequence, but only fully completed exercises progress.
- **The active program is data, not the schema.** `BuiltInPrograms` defines programs; `FreeLiftRepository.materializeProgram`/`switchProgram` write them into core slots, day mappings, and accessory assignments. Day identity is a free-text key (`"A"`,`"B"`,`"C"`…), not an enum. Everything a program prescribes is required and gates completion; only user-added accessories are optional (`accessory_assignments.required`).
- **Room schema changes require a migration plus schema JSON.** Schemas are exported to `app/schemas/` (also fed to androidTest assets); the `DatabaseMigrationTest` instrumented test must cover any new version. Current version: 3.

## Tests

- `app/src/test/` — JVM unit tests for domain math, reminders, CSV escaping (plain JUnit4, no Robolectric).
- `app/src/androidTest/` — instrumented: Room migration, repository transactions/progression, timer persistence (including screen-off with notification permission denied), and the main Compose flow (`AppFlowTest`).

New domain logic belongs in `domain/` with a JVM unit test; reserve instrumented tests for things that genuinely need a device (Room, services, Compose UI).
