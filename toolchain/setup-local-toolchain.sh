#!/usr/bin/env bash
# Recreates the local Android build toolchain used for verification in this sandbox.
# Not part of the app build: on a normal machine these come from the Android SDK.
set -euo pipefail
ROOT=/home/user/.toolchain
CACHE=/tmp/tc-cache
mkdir -p "$ROOT" "$CACHE"

# --- JRE (kotlinc / d8 / apksigner only need a runtime)
if [ ! -x "$ROOT/jre/bin/java" ]; then
  pip download jdk4py --no-deps -d "$CACHE/jdk" -q
  rm -rf "$CACHE/jdkx" && mkdir -p "$CACHE/jdkx"
  (cd "$CACHE/jdkx" && unzip -q "$CACHE"/jdk/*.whl)
  rm -rf "$ROOT/jre" && cp -r "$CACHE/jdkx/jdk4py/java-runtime" "$ROOT/jre"
fi
echo "jre: $("$ROOT/jre/bin/java" -version 2>&1 | head -1)"

# --- Kotlin compiler
if [ ! -x "$ROOT/kotlinc/bin/kotlinc-jvm" ]; then
  (cd "$CACHE" && npm pack kotlin-compiler@2.4.21 --silent >/dev/null)
  rm -rf "$CACHE/kc" && mkdir -p "$CACHE/kc"
  tar xzf "$CACHE"/kotlin-compiler-2.4.21.tgz -C "$CACHE/kc"
  rm -rf "$ROOT/kotlinc" && mv "$CACHE/kc/package" "$ROOT/kotlinc"
fi
echo "kotlin: $("$ROOT/kotlinc/bin/kotlinc-jvm" -version 2>&1 | head -1)"

# --- Android build-tools + platform (mirrored SDK bundle, downloaded through the GitHub API)
gh_blob() {
  local repo="$1" path="$2" out="$3"
  local sha
  sha=$(curl -sS -m 30 "https://api.github.com/repos/$repo/contents/$path" | python3 -c 'import json,sys;print(json.load(sys.stdin)["sha"])')
  curl -sS -m 900 "https://api.github.com/repos/$repo/git/blobs/$sha" \
    | python3 -c "import json,sys,base64;open('$out','wb').write(base64.b64decode(json.load(sys.stdin)['content']))"
}

if [ ! -x "$ROOT/build-tools/aapt2" ]; then
  [ -f "$CACHE/bt.zip" ] || gh_blob whynusn/android-sdk-bundle build-tools_r34-linux.zip "$CACHE/bt.zip"
  rm -rf "$CACHE/bt" && mkdir -p "$CACHE/bt"
  unzip -q "$CACHE/bt.zip" -d "$CACHE/bt"
  mkdir -p "$ROOT/build-tools"
  cp "$CACHE/bt/android-14"/{aapt2,zipalign,d8,apksigner,core-lambda-stubs.jar} "$ROOT/build-tools/"
  cp -r "$CACHE/bt/android-14"/{lib,lib64} "$ROOT/build-tools/"
  chmod +x "$ROOT/build-tools"/{aapt2,zipalign,d8,apksigner}
fi
echo "aapt2: $("$ROOT/build-tools/aapt2" version)"

if [ ! -f "$ROOT/platforms/android-34/android.jar" ]; then
  [ -f "$CACHE/p34.zip" ] || gh_blob whynusn/android-sdk-bundle platform-34_r03.zip "$CACHE/p34.zip"
  rm -rf "$CACHE/p34" && mkdir -p "$CACHE/p34"
  unzip -q "$CACHE/p34.zip" -d "$CACHE/p34"
  mkdir -p "$ROOT/platforms/android-34"
  cp "$CACHE/p34/android-34/android.jar" "$ROOT/platforms/android-34/android.jar"
fi
echo "android.jar: $(du -h "$ROOT/platforms/android-34/android.jar" | cut -f1)"

# --- ANDROID_HOME shim so toolchain/build.sh finds everything the normal way
SDK=/home/user/.toolchain-sdk
mkdir -p "$SDK/build-tools" "$SDK/platforms"
ln -sfn "$ROOT/build-tools" "$SDK/build-tools/34.0.0"
ln -sfn "$ROOT/platforms/android-34" "$SDK/platforms/android-34"
echo "ready"
