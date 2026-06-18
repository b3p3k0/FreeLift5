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
- Timer completion needs an explicit foreground cue, not just a state label.
  If the Workout screen is visible, fire the in-app sound/haptic cue once and
  use an actual border pulse so the user can tell rest has ended without relying
  on background notification behavior.
- Start rest timers only when the active workout still has open work sets after
  saving. The final saved set should clear the timer surface and move the user
  toward review, not start a new rest interval.
- Progress markers should not use placeholder numbers that look like completed
  data. Leave pending workout sets blank and change the marker styling only when
  a saved set exists.
- Progress charts need visible units, compact date labels, and accessible
  summaries. A line without axes may be decorative, but it is not useful
  training feedback.

## Release Continuity

- Do not hardcode user-facing app versions in Compose. Read the build version
  metadata and scrub README download links, release notes, and About copy before
  every tagged APK.

## Privacy Boundary

- External web links do not require adding app network permission. Use Android's
  URL intent handoff and keep the literal URL visible so the app remains offline
  even when users choose to open documentation in their browser.
