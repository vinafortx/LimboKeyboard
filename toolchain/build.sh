#!/usr/bin/env bash
#
# Limbo Keyboard — build without Gradle.
#
# The whole toolchain is the same set of official pieces Gradle would call for you:
#   aapt2  → compile resources and link them into a base APK
#   kotlinc→ compile Kotlin to JVM bytecode
#   d8     → translate bytecode into DEX
#   zipalign / apksigner → align and sign the result
#
# Typical use:
#   ./toolchain/build.sh                 # debug build, auto-detects the SDK
#   ./toolchain/build.sh --release       # release build with a persistent keystore
#   ANDROID_HOME=~/Android/Sdk ./toolchain/build.sh
#
# Everything lands in ./dist and nothing is written outside the repository.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP="$ROOT/app/src/main"
WORK="$ROOT/toolchain/.work"
DIST="$ROOT/dist"
KEYS="$ROOT/toolchain/.keys"
BUILD_TYPE="debug"

for arg in "$@"; do
  case "$arg" in
    --release) BUILD_TYPE="release" ;;
    --debug) BUILD_TYPE="debug" ;;
    -h|--help) sed -n '2,20p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

log()  { printf '\033[38;5;111m▸\033[0m %s\n' "$*"; }
ok()   { printf '\033[38;5;114m✓\033[0m %s\n' "$*"; }
fail() { printf '\033[38;5;203m✗\033[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- locate Java
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA="$JAVA_HOME/bin/java"
  KEYTOOL="$JAVA_HOME/bin/keytool"
else
  JAVA="$(command -v java || true)"
  KEYTOOL="$(command -v keytool || true)"
fi
[[ -n "$JAVA" ]] || fail "Java not found. Install a JDK 17+ or set JAVA_HOME."
[[ -n "$KEYTOOL" ]] || fail "keytool not found. A full JDK is required for signing."

# ---------------------------------------------------------------- locate the SDK
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[[ -n "$SDK" ]] || fail "Android SDK not found. Set ANDROID_HOME (or ANDROID_SDK_ROOT)."

BUILD_TOOLS_DIR="${BUILD_TOOLS_DIR:-}"
if [[ -z "$BUILD_TOOLS_DIR" ]]; then
  latest="$(ls -1 "$SDK/build-tools" 2>/dev/null | sort -V | tail -1 || true)"
  [[ -n "$latest" ]] || fail "No build-tools in $SDK/build-tools"
  BUILD_TOOLS_DIR="$SDK/build-tools/$latest"
fi

PLATFORM_DIR="${PLATFORM_DIR:-}"
if [[ -z "$PLATFORM_DIR" ]]; then
  target="${COMPILE_SDK:-34}"
  if [[ -f "$SDK/platforms/android-$target/android.jar" ]]; then
    PLATFORM_DIR="$SDK/platforms/android-$target"
  else
    latest_platform="$(ls -1 "$SDK/platforms" 2>/dev/null | sort -V | tail -1 || true)"
    [[ -n "$latest_platform" ]] || fail "No platforms in $SDK/platforms"
    PLATFORM_DIR="$SDK/platforms/$latest_platform"
  fi
fi
ANDROID_JAR="$PLATFORM_DIR/android.jar"
[[ -f "$ANDROID_JAR" ]] || fail "android.jar missing in $PLATFORM_DIR"

AAPT2="$BUILD_TOOLS_DIR/aapt2"
D8="$BUILD_TOOLS_DIR/d8"
ZIPALIGN="$BUILD_TOOLS_DIR/zipalign"
APKSIGNER="$BUILD_TOOLS_DIR/apksigner"
for tool in "$AAPT2" "$D8" "$ZIPALIGN" "$APKSIGNER"; do
  [[ -x "$tool" ]] || fail "Missing ${tool##*/} in $BUILD_TOOLS_DIR"
done

# d8/apksigner are Java programs behind shell wrappers that need java on PATH.
export PATH="$(dirname "$JAVA"):$PATH"
export JAVA_HOME="${JAVA_HOME:-$(cd "$(dirname "$JAVA")/.." && pwd)}"

# ---------------------------------------------------------------- locate kotlinc
KOTLINC="${KOTLINC:-$(command -v kotlinc || true)}"
[[ -n "$KOTLINC" ]] || fail "kotlinc not found. Install the Kotlin compiler or set KOTLINC=/path/to/kotlinc"
if [[ -n "${KOTLIN_HOME:-}" ]]; then
  STDLIB="$KOTLIN_HOME/lib/kotlin-stdlib.jar"
else
  STDLIB="$(dirname "$(dirname "$(readlink -f "$KOTLINC")")")/lib/kotlin-stdlib.jar"
fi
[[ -f "$STDLIB" ]] || fail "kotlin-stdlib.jar not found (looked at $STDLIB). Set KOTLIN_HOME."

MIN_SDK=26
TARGET_SDK=34
VERSION_CODE=1
VERSION_NAME="1.0.0"

log "Java      $(basename "$JAVA")"
log "Build tools ${BUILD_TOOLS_DIR##*/}"
log "Platform  ${PLATFORM_DIR##*/}"
log "Kotlin    $("$KOTLINC" -version 2>&1 | head -1)"

rm -rf "$WORK"
mkdir -p "$WORK"/{res,gen,classes,dex} "$DIST"

# ---------------------------------------------------------------- 1. resources
log "Compiling resources"
"$AAPT2" compile --dir "$APP/res" -o "$WORK/res/resources.zip"

# ---------------------------------------------------------------- 2. link
# aapt2 needs the package name inside the manifest; Gradle would take it from the build file,
# so the manual path writes a temporary copy instead of polluting the source.
log "Linking resources"
sed 's|<manifest |<manifest package="app.limbo.keyboard" |' \
  "$APP/AndroidManifest.xml" > "$WORK/AndroidManifest.xml"

"$AAPT2" link \
  -o "$WORK/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$WORK/AndroidManifest.xml" \
  -R "$WORK/res/resources.zip" \
  --java "$WORK/gen" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  --auto-add-overlay

# ---------------------------------------------------------------- 3. kotlin
log "Compiling Kotlin"
mapfile -t SOURCES < <(find "$APP/java" -name '*.kt' | sort)
"$KOTLINC" "${SOURCES[@]}" \
  -classpath "$ANDROID_JAR:$STDLIB" \
  -jvm-target 17 \
  -nowarn \
  -d "$WORK/classes"

# ---------------------------------------------------------------- 4. dex
log "Dexing"
( cd "$WORK/classes" && zip -q -r "$WORK/classes.jar" . )
if ! "$D8" --release --min-api "$MIN_SDK" --lib "$ANDROID_JAR" \
      --output "$WORK/dex" "$WORK/classes.jar" "$STDLIB" > "$WORK/d8.log" 2>&1; then
  cat "$WORK/d8.log" >&2
  fail "Dexing failed"
fi
# D8 from an older build-tools release cannot parse the newest Kotlin metadata; the bytecode
# itself is fine, so those notices are noise here.
grep -E "^(Error|Warning: (?!An error occurred when parsing kotlin metadata))" "$WORK/d8.log" 2>/dev/null || true

# ---------------------------------------------------------------- 5. package
log "Packaging"
cp "$WORK/base.apk" "$WORK/unsigned.apk"
( cd "$WORK/dex" && zip -q "$WORK/unsigned.apk" classes.dex )
"$ZIPALIGN" -f -p 4 "$WORK/unsigned.apk" "$WORK/aligned.apk"

# ---------------------------------------------------------------- 6. sign
mkdir -p "$KEYS"
KEYSTORE="$KEYS/limbo-${BUILD_TYPE}.jks"
STORE_PASS="limbo-keyboard"
if [[ ! -f "$KEYSTORE" ]]; then
  log "Creating a keystore for $BUILD_TYPE builds"
  "$KEYTOOL" -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass "$STORE_PASS" \
    -keypass "$STORE_PASS" \
    -alias limbo \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Limbo Keyboard, OU=Build, O=Limbo, L=, S=, C=" >/dev/null 2>&1
fi

OUT="$DIST/Limbo-$BUILD_TYPE.apk"
"$APKSIGNER" sign \
  --ks "$KEYSTORE" \
  --ks-pass "pass:$STORE_PASS" \
  --key-pass "pass:$STORE_PASS" \
  --ks-key-alias limbo \
  --min-sdk-version "$MIN_SDK" \
  --out "$OUT" \
  "$WORK/aligned.apk"

"$APKSIGNER" verify --min-sdk-version "$MIN_SDK" "$OUT" >/dev/null

ok "Built $OUT ($(du -h "$OUT" | cut -f1))"
ok "Signed with $KEYSTORE"
printf '\nInstall with:\n  adb install -r %s\n\n' "$OUT"
