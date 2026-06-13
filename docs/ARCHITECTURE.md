# FreeLift5 Architecture

## Layers

- `domain/`: pure Kotlin programs, progression, warmup, plate, and weight math.
- `data/`: Room entities/DAO/repository plus DataStore preferences.
- `ui/`: Jetpack Compose screens and the application view model.
- `timer/`: persisted deadline state and the optional foreground timer service.
- `reminders/`: approximate, opt-in WorkManager reminders.
- `export/`: versioned workout/measurement CSV and ZIP creation.

## Programs

Programs are data, not code. `domain/BuiltInPrograms` is a registry of
`ProgramDefinition`s — Original 5x5, Lite, Mini, Plus, and a dumbbell Quarantine
routine — plus the built-in exercise catalog. Adding a linear program means
appending one definition. A program is an ordered list of days, each with core
lifts (`SlotDef`) and required assistance (`AccessoryDef`); rotation cycles the
days, so two-, three-, and four-day programs all work without special cases.

`FreeLiftRepository.materializeProgram` turns a definition into live core slots,
day mappings, and accessory assignments. `switchProgram` swaps programs while
leaving workout history untouched and carrying working weights for any lift whose
canonical slot the new program reuses. Settings hold `activeProgramId` and the
`nextWorkoutDayKey` cursor.

Everything a program prescribes is required and counts toward completing a
workout; accessories the user adds are optional (an `accessory_assignments.required`
flag distinguishes them). Set schemes and progression policies are sealed types:
this phase ships straight sets with per-workout linear progression and leaves
ramped/weekly variants (Madcow, Intermediate, Ultra) for later.

## Persistence

Weights are stored as integer grams. Unit conversion occurs only at input,
display, and export boundaries.

Every saved set is immediately written to Room. `ExerciseSessionEntity` is an
immutable snapshot of the exercise name, tracking mode, prescription, load,
increments, and timers used when a workout began. Editing the current program
therefore does not alter history.

Core progression is keyed by core slot. The shared Squat slot is referenced by
both A and B until the user explicitly splits it. A replacement changes the
exercise assigned to a slot while preserving the slot's program responsibility.

Settings and reminder choices use DataStore. The active rest timer stores an
absolute deadline in private `SharedPreferences`, allowing reconstruction after
rotation, activity recreation, backgrounding, or process death.

## Privacy Boundary

FreeLift5 has no network permission or networking dependency. Room, DataStore,
timer state, and export staging remain in app-private storage. Android cloud
backup and device-transfer backup are disabled. Export destinations and share
targets are chosen explicitly by the user through Android system UI.

The only runtime permission is Android 13+ notification access, requested after
the user opts into background timer alerts or reminders.

## Tests

- Pure Kotlin unit tests cover routine defaults, progression and deloads,
  rounding, e1RM, warmups, plate loading, reminders, and CSV escaping.
- Android tests validate Room migration `1 -> 2`, repository transactions,
  partial-workout progression, weighted and timed accessories, timer persistence
  with the screen off and notification permission denied, and the principal
  Compose user flow. The complete suite passes on API 28 and API 36 emulators.
