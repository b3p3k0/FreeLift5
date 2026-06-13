#!/usr/bin/env bash
set -euo pipefail

AVD_NAME="${1:-}"
if [[ -z "$AVD_NAME" ]]; then
    printf 'Usage: %s <avd-name>\n' "$0" >&2
    exit 1
fi

find_java_home() {
    if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
        printf '%s\n' "$JAVA_HOME"
        return
    fi

    local candidate
    for candidate in \
        /opt/android-studio/android-studio/jbr \
        /opt/android-studio/jbr \
        /usr/local/android-studio/jbr \
        "$HOME/android-studio/jbr" \
        "$HOME/.local/share/JetBrains/Toolbox/apps/android-studio"/*/*/jbr \
        /opt/android-studio-*/android-studio/jbr; do
        if [[ -x "$candidate/bin/java" ]]; then
            printf '%s\n' "$candidate"
            return
        fi
    done

    printf 'Set JAVA_HOME to Android Studio bundled JDK and rerun.\n' >&2
    exit 1
}

JAVA_HOME="$(find_java_home)"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT ANDROID_HOME

ADB="$ANDROID_SDK_ROOT/platform-tools/adb"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"

if [[ ! -x "$ADB" || ! -x "$EMULATOR" ]]; then
    printf 'Android SDK tools are missing. Run bootstrap-android-linux.sh first.\n' >&2
    exit 1
fi

if ! "$EMULATOR" -list-avds | grep -Fxq "$AVD_NAME"; then
    printf 'AVD %s does not exist. Available AVDs:\n' "$AVD_NAME" >&2
    "$EMULATOR" -list-avds >&2
    exit 1
fi

if "$ADB" devices | tail -n +2 | grep -q $'\tdevice$'; then
    printf '%s\n' \
        "A device or emulator is already connected." \
        "Shut it down before running this helper so tests target one device." >&2
    exit 1
fi

EMULATOR_LOG="$(mktemp -t freelift5-emulator.XXXXXX.log)"
EMULATOR_PID=""

cleanup() {
    if "$ADB" devices | tail -n +2 | grep -q $'\tdevice$'; then
        "$ADB" emu kill >/dev/null 2>&1 || true
    fi
    if [[ -n "$EMULATOR_PID" ]]; then
        wait "$EMULATOR_PID" 2>/dev/null || true
    fi
    rm -f "$EMULATOR_LOG"
}
trap cleanup EXIT INT TERM

printf 'Starting %s headlessly...\n' "$AVD_NAME"
"$EMULATOR" \
    -avd "$AVD_NAME" \
    -no-window \
    -no-audio \
    -no-boot-anim \
    -no-snapshot-save \
    -gpu swiftshader_indirect \
    >"$EMULATOR_LOG" 2>&1 &
EMULATOR_PID=$!

"$ADB" start-server >/dev/null
for _ in $(seq 1 120); do
    if "$ADB" devices | tail -n +2 | grep -q $'\tdevice$'; then
        break
    fi
    if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
        cat "$EMULATOR_LOG" >&2
        exit 1
    fi
    sleep 1
done

if ! "$ADB" devices | tail -n +2 | grep -q $'\tdevice$'; then
    cat "$EMULATOR_LOG" >&2
    printf 'Timed out waiting for %s to connect to ADB.\n' "$AVD_NAME" >&2
    exit 1
fi

for _ in $(seq 1 120); do
    if [[ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; then
        break
    fi
    if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
        cat "$EMULATOR_LOG" >&2
        exit 1
    fi
    sleep 2
done

if [[ "$("$ADB" shell getprop sys.boot_completed | tr -d '\r')" != "1" ]]; then
    cat "$EMULATOR_LOG" >&2
    printf 'Timed out waiting for %s to boot.\n' "$AVD_NAME" >&2
    exit 1
fi

"$ADB" shell input keyevent 82 >/dev/null
printf 'Running tests on Android API %s...\n' \
    "$("$ADB" shell getprop ro.build.version.sdk | tr -d '\r')"
./gradlew connectedDebugAndroidTest
