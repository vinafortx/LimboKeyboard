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
ABI_SPLITS=0
OUT_DIR=""

for arg in "$@"; do
  case "$arg" in
    --release) BUILD_TYPE="release" ;;
    --debug) BUILD_TYPE="debug" ;;
    --abi-splits) ABI_SPLITS=1 ;;
    --out=*) OUT_DIR="${arg#--out=}" ;;
    -h|--help) sed -n '2,26p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if [[ -n "$OUT_DIR" ]]; then
  DIST="$ROOT/$OUT_DIR"
fi

log()  { printf '\033[38;5;111m▸\033[0m %s\n' "$*"; }
ok()   { printf '\033[38;5;114m✓\033[0m %s\n' "$*"; }
fail() { printf '\033[38;5;203m✗\033[0m %s\n' "$*" >&2; exit 1; }
warn() { printf '\033[38;5;179m!\033[0m %s\n' "$*" >&2; }

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
VERSION_CODE=3
VERSION_NAME="1.0.1"

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
KEYSTORE="${LIMBO_KEYSTORE:-$KEYS/limbo-${BUILD_TYPE}.jks}"
STORE_PASS="${LIMBO_STORE_PASSWORD:-limbo-keyboard}"
KEY_ALIAS="${LIMBO_KEY_ALIAS:-limbo}"
KEY_PASS="${LIMBO_KEY_PASSWORD:-$STORE_PASS}"

if [[ ! -f "$KEYSTORE" ]]; then
  log "Creating a keystore for $BUILD_TYPE builds ($KEYSTORE)"
  "$KEYTOOL" -genkeypair -v \
    -keystore "$KEYSTORE" \
    -storepass "$STORE_PASS" \
    -keypass "$KEY_PASS" \
    -alias "$KEY_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Limbo Keyboard, OU=Build, O=Limbo, L=, S=, C=" >/dev/null 2>&1
fi

sign() {  # sign <in> <out>
  "$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$STORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --min-sdk-version "$MIN_SDK" \
    --v2-signing-enabled true --v3-signing-enabled true \
    --out "$2" "$1"
  "$APKSIGNER" verify --min-sdk-version "$MIN_SDK" "$2" >/dev/null
  rm -f "$2.idsig"  # v4 signature sidecar; the release ships v2/v3 only
}

OUT="$DIST/Limbo-$BUILD_TYPE.apk"
sign "$WORK/aligned.apk" "$OUT"

ok "Built $OUT ($(du -h "$OUT" | cut -f1))"
ok "Signed with $KEYSTORE"

# ---------------------------------------------------------------- 7. ABI splits
# A pure-Kotlin app carries one DEX for every CPU, so per-ABI APKs would be identical files.
# The split step therefore only runs when the package actually contains native libraries, or
# when --abi-splits is passed explicitly (useful once an .so shows up).
NATIVE_LIBS="$(unzip -l "$WORK/aligned.apk" | awk '{print $4}' | grep -E '^lib/[^/]+/' | cut -d/ -f2 | sort -u || true)"
if [[ -n "$NATIVE_LIBS" ]]; then
  log "Native libraries found: $(echo "$NATIVE_LIBS" | tr '\n' ' ')"
  SUFFIX="-universal"
  mv "$OUT" "$DIST/Limbo-$BUILD_TYPE$SUFFIX.apk"
  OUT="$DIST/Limbo-$BUILD_TYPE$SUFFIX.apk"
  for abi in $NATIVE_LIBS; do
    SPLIT="$WORK/split-$abi.apk"
    rm -rf "$WORK/split" && mkdir -p "$WORK/split"
    unzip -q "$WORK/aligned.apk" -d "$WORK/split"
    # keep this ABI's libraries and drop every other one
    if [[ -d "$WORK/split/lib" ]]; then
      for dir in "$WORK/split/lib"/*; do
        [[ "$(basename "$dir")" == "$abi" ]] || rm -rf "$dir"
      done
    fi
    (cd "$WORK/split" && zip -q -r "$SPLIT" .)
    sign "$SPLIT" "$DIST/Limbo-$BUILD_TYPE-$abi.apk"
    ok "Split: $DIST/Limbo-$BUILD_TYPE-$abi.apk ($(du -h "$DIST/Limbo-$BUILD_TYPE-$abi.apk" | cut -f1))"
  done
  ok "Universal APK kept alongside the splits"
elif [[ "$ABI_SPLITS" == "1" ]]; then
  warn "No native libraries in the package: the DEX is shared by every CPU, so per-ABI APKs would be identical copies. Ship the universal build."
else
  log "No native libraries: universal APK is the only meaningful artefact"
fi

printf '\nInstall with:\n  adb install -r %s\n\n' "$OUT"
