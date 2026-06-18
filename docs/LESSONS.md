# FreeLift5 Lessons

This file records reusable engineering guardrails discovered while changing the
app. Keep entries concise and tied to a concrete failure mode.

## Theme Engine

- Persist stable theme IDs, not palette values. Parse every stored value through
  brightness-aware fallbacks so unknown or mismatched data cannot select an
  invalid day or night theme.
- Resolve the active theme once at the app root. Applying a complete Material
  color scheme there keeps selection immediate and prevents screen-level color
  drift.
- Match launch-window resources to the default light and dark themes. Compose
  cannot prevent a bright startup flash before its first frame.
- Synchronize status and navigation icon brightness with the resolved theme.
  System bars are part of the visible color contract.
- Keep appearance preferences out of versioned workout exports. Presentation
  state is device-local unless the export schema explicitly adopts it.
- Build documentation images from equal-size native captures in a temporary
  directory. Validate first, strip metadata, and publish only a complete set so
  a failed gallery run cannot leave mixed output.
- Report device-matrix results per API level. A passing modern emulator does not
  substitute for an unavailable minimum-SDK image.

## Built-In Programs

- Treat built-in program IDs and names as release-facing contracts. Before the
  first public APK, remove unsuitable IDs entirely; after release, preserve old
  IDs with explicit aliases or migrations.
- Drive workout helper UI from explicit exercise equipment metadata. Dumbbell
  programs should not inherit barbell-only copy, warmup assumptions, or plate
  calculators just because they track weight.

## Android System UI

- Treat bottom action rows as edge-to-edge content. Plain custom bottom bars
  need explicit navigation-bar inset padding; Material navigation components may
  handle this themselves, but custom rows will otherwise sit under gesture or
  three-button navigation.

## Release Continuity

- Do not hardcode user-facing app versions in Compose. Read the build version
  metadata and scrub README download links, release notes, and About copy before
  every tagged APK.
