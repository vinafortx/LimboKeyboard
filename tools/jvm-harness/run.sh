#!/usr/bin/env bash
#
# Headless smoke test for the whole app, on a plain JVM.
#
# The Android framework is replaced by the small stand-ins in ./shim: enough of Activity, View,
# Canvas, Choreographer, Handler and InputMethodService to build every screen, measure it, lay it
# out, draw it and let its springs run, without a device or an emulator.
#
# It is not a pixel-perfect emulator — the shim reports zero-sized text and empty resources — but
# it does catch crashes in lifecycle code, view construction and the drawing path, which is where
# a keyboard usually breaks.
#
#   ./tools/jvm-harness/run.sh              # lifecycle only
#   ./tools/jvm-harness/run.sh --draw       # also measure, lay out and draw every screen
#
# Needs a JDK 17+ and kotlinc-jvm. Both are found in PATH, in KOTLIN_HOME / JAVA_HOME, or in the
# repository's own .toolchain used by toolchain/build.sh.
#
# Output goes to tools/jvm-harness/.build and is ignored by git.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
HERE="$ROOT/tools/jvm-harness"
BUILD="$HERE/.build"
STDLIB=""

DRAW=0
for arg in "$@"; do
  case "$arg" in
    --draw) DRAW=1 ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

log() { printf '\033[38;5;111m▸\033[0m %s\n' "$*"; }
ok()  { printf '\033[38;5;114m✓\033[0m %s\n' "$*"; }
fail() { printf '\033[38;5;203m✗\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- Kotlin
KOTLIN_HOME="${KOTLIN_HOME:-}"
KOTLINC="${KOTLINC:-}"

if [[ -z "$KOTLINC" ]]; then
  for candidate in \
    "${KOTLIN_HOME:+$KOTLIN_HOME/bin/kotlinc-jvm}" \
    "$ROOT/../.toolchain/kotlinc/bin/kotlinc-jvm" \
    "$HOME/.toolchain/kotlinc/bin/kotlinc-jvm" \
    "$(command -v kotlinc-jvm || true)"
  do
    [[ -n "$candidate" && -x "$candidate" ]] && { KOTLINC="$candidate"; break; }
  done
fi
[[ -n "$KOTLINC" ]] || fail "kotlinc-jvm not found. Set KOTLINC=/path/to/kotlinc-jvm (JDK 17+ required)."
if [[ -z "$KOTLIN_HOME" ]]; then
  KOTLIN_HOME="$(cd "$(dirname "$KOTLINC")/.." && pwd)"
fi
STDLIB="${KOTLIN_STDLIB:-$KOTLIN_HOME/lib/kotlin-stdlib.jar}"
[[ -f "$STDLIB" ]] || fail "kotlin-stdlib.jar not found: $STDLIB"

# ---------------------------------------------------------------- Android stubs
ANDROID_JAR="${ANDROID_JAR:-}"
if [[ -z "$ANDROID_JAR" ]]; then
  for candidate in \
    "${ANDROID_HOME:-}/platforms/android-34/android.jar" \
    "$HOME/.toolchain/platforms/android-34/android.jar" \
    "$ROOT/../.toolchain/platforms/android-34/android.jar" \
    "$HOME/Android/Sdk/platforms/android-34/android.jar"
  do
    [[ -f "$candidate" ]] && { ANDROID_JAR="$candidate"; break; }
  done
fi
[[ -n "$ANDROID_JAR" && -f "$ANDROID_JAR" ]] || fail "android.jar (API 34) not found. Set ANDROID_JAR=/path/to/android.jar"

JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
command -v "$JAVA" >/dev/null || fail "java not found. Set JAVA_HOME."

# ---------------------------------------------------------------- build the three layers
rm -rf "$BUILD"
mkdir -p "$BUILD"

log "Framework stand-ins"
"$KOTLINC" "$HERE"/shim/*.kt -nowarn -d "$BUILD/shim" 2>&1 | grep -v '^WARNING' || true

log "Application classes"
# The app is compiled against the real android.jar, exactly as the release build does, so the
# signatures stay honest; the shim only steps in at run time.
"$KOTLINC" $(find "$ROOT/app/src/main/java" -name '*.kt') \
  -classpath "$ANDROID_JAR:$STDLIB" -jvm-target 17 -nowarn -d "$BUILD/app" 2>&1 | grep -v '^WARNING' || true

log "Harness"
"$KOTLINC" "$HERE"/harness/Main.kt \
  -classpath "$BUILD/shim:$BUILD/app:$STDLIB" -jvm-target 17 -nowarn -d "$BUILD/harness" 2>&1 | grep -v '^WARNING' || true

# ---------------------------------------------------------------- run
log "Running every screen (ru, en), then the keyboard"
CP="$BUILD/harness:$BUILD/shim:$BUILD/app:$STDLIB"
EXERCISE="$DRAW" "$JAVA" -cp "$CP" harness.MainKt

ok "Harness finished"
