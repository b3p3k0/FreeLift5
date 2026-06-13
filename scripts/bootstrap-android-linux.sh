#!/usr/bin/env bash
set -euo pipefail

if [[ "$(uname -s)" != "Linux" ]]; then
    printf 'This helper currently supports Linux hosts only.\n' >&2
    exit 1
fi

if [[ "$(uname -m)" != "x86_64" ]]; then
    printf '%s\n' \
        "This helper installs the validated x86_64 emulator images." \
        "On ARM64, create API 28 and API 36 arm64-v8a AVDs in Device Manager." >&2
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

    printf '%s\n' \
        "Could not locate Android Studio's bundled JDK." \
        "Set JAVA_HOME=/path/to/android-studio/jbr and rerun." >&2
    exit 1
}

JAVA_HOME="$(find_java_home)"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT ANDROID_HOME

SDK_MANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
AVD_MANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"

if [[ ! -x "$SDK_MANAGER" || ! -x "$AVD_MANAGER" ]]; then
    cat >&2 <<EOF
Android SDK command-line tools were not found at:
  $ANDROID_SDK_ROOT/cmdline-tools/latest/bin

Launch Android Studio, then install "Android SDK Command-line Tools (latest)"
from SDK Manager > SDK Tools. Rerun this script afterward.
EOF
    exit 1
fi

printf 'Using JAVA_HOME=%s\n' "$JAVA_HOME"
printf 'Using ANDROID_SDK_ROOT=%s\n' "$ANDROID_SDK_ROOT"

yes | "$SDK_MANAGER" --licenses >/dev/null || true
"$SDK_MANAGER" \
    "platform-tools" \
    "emulator" \
    "platforms;android-36" \
    "build-tools;36.1.0" \
    "system-images;android-28;google_apis;x86_64" \
    "system-images;android-36;google_apis_ps16k;x86_64"

create_avd() {
    local name="$1"
    local image="$2"
    local device="$3"

    if "$EMULATOR" -list-avds | grep -Fxq "$name"; then
        printf 'AVD %s already exists.\n' "$name"
        return
    fi

    printf 'Creating AVD %s...\n' "$name"
    echo no | "$AVD_MANAGER" create avd \
        --name "$name" \
        --package "$image" \
        --device "$device"
}

create_avd \
    "FreeLift5_API28" \
    "system-images;android-28;google_apis;x86_64" \
    "pixel_2"
create_avd \
    "FreeLift5_API36" \
    "system-images;android-36;google_apis_ps16k;x86_64" \
    "pixel_9"

printf 'sdk.dir=%s\n' "$ANDROID_SDK_ROOT" > local.properties
chmod +x gradlew scripts/*.sh

printf '\nEnvironment ready. Available AVDs:\n'
"$EMULATOR" -list-avds
printf '\nNext run:\n'
printf '  ./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease\n'
printf '  ./scripts/test-avd.sh FreeLift5_API28\n'
printf '  ./scripts/test-avd.sh FreeLift5_API36\n'
