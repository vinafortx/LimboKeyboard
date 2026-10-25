#!/usr/bin/env bash
#
# Cuts a Limbo Keyboard release.
#
#   ./toolchain/release.sh                 # build with the manual toolchain and tag the next patch
#   ./toolchain/release.sh --version 1.1.0
#   ./toolchain/release.sh --draft         # create the release but do not publish it
#   ./toolchain/release.sh --no-publish    # only build, put files in dist/release
#
# What it does:
#   1. bumps versionName/versionCode in app/build.gradle.kts and toolchain/build.sh
#   2. builds a signed release APK (and ABI splits, when the app actually has native code)
#   3. writes SHA256SUMS and a short notes file
#   4. creates a GitHub release and uploads the artefacts (unless --no-publish)
#
# Signing: export LIMBO_KEYSTORE / LIMBO_STORE_PASSWORD / LIMBO_KEY_ALIAS /
# LIMBO_KEY_PASSWORD, or let the script use the throwaway key in toolchain/.keys.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

VERSION=""
NOTES_FILE=""
DRAFT=0
PUBLISH=1
BUMP=1
TARGET=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --version) VERSION="${2:?}"; shift 2 ;;
    --notes) NOTES_FILE="${2:?}"; shift 2 ;;
    --no-bump) BUMP=0; shift ;;
    --target) TARGET="${2:?}"; shift 2 ;;
    --draft) DRAFT=1; shift ;;
    --no-publish) PUBLISH=0; shift ;;
    -h|--help) sed -n '2,24p' "$0"; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

log()  { printf '\033[38;5;111m▸\033[0m %s\n' "$*"; }
ok()   { printf '\033[38;5;114m✓\033[0m %s\n' "$*"; }
fail() { printf '\033[38;5;203m✗\033[0m %s\n' "$*" >&2; exit 1; }

CURRENT_NAME="$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts | head -1)"
CURRENT_CODE="$(sed -n 's/.*versionCode = \([0-9]*\).*/\1/p' app/build.gradle.kts | head -1)"
[[ -n "$CURRENT_NAME" ]] || fail "Could not read versionName from app/build.gradle.kts"

if [[ -z "$VERSION" ]]; then
  if [[ "$BUMP" == "1" ]]; then
    IFS=. read -r major minor patch <<< "$CURRENT_NAME"
    VERSION="$major.$minor.$((patch + 1))"
  else
    VERSION="$CURRENT_NAME"
  fi
fi
NEXT_CODE=$((CURRENT_CODE + 1))

if [[ "$BUMP" == "1" ]]; then
log "Releasing $CURRENT_NAME ($CURRENT_CODE) -> $VERSION ($NEXT_CODE)"

# ------------------------------------------------------------------ version bump
python3 - "$VERSION" "$NEXT_CODE" <<'PY'
import re, sys
version, code = sys.argv[1], sys.argv[2]
gradle = "app/build.gradle.kts"
text = open(gradle, encoding="utf-8").read()
text = re.sub(r'versionCode = \d+', f'versionCode = {code}', text, count=1)
text = re.sub(r'versionName = "[^"]+"', f'versionName = "{version}"', text, count=1)
open(gradle, "w", encoding="utf-8").write(text)

script = "toolchain/build.sh"
text = open(script, encoding="utf-8").read()
text = re.sub(r'VERSION_CODE=\d+', f'VERSION_CODE={code}', text, count=1)
text = re.sub(r'VERSION_NAME="[^"]+"', f'VERSION_NAME="{version}"', text, count=1)
open(script, "w", encoding="utf-8").write(text)
print(f"version bumped to {version} ({code})")
PY
else
  log "Releasing $VERSION ($CURRENT_CODE) without bumping"
fi

# ------------------------------------------------------------------ build
RELEASE_DIR="$ROOT/dist/release/$VERSION"
rm -rf "$RELEASE_DIR"
mkdir -p "$RELEASE_DIR"

log "Building release artefacts"
./toolchain/build.sh --release --out="dist/release/$VERSION" > "$RELEASE_DIR/build.log" 2>&1 || {
  tail -30 "$RELEASE_DIR/build.log" >&2
  fail "Build failed"
}
rm -f "$RELEASE_DIR/build.log"

APKS=("$RELEASE_DIR"/*.apk)
[[ -e "${APKS[0]}" ]] || fail "No APK produced"
ok "Built $(printf '%s ' "${APKS[@]##*/}")"

# The manual toolchain names files Limbo-release.apk; give them the version for the release page.
for apk in "${APKS[@]}"; do
  base="${apk##*/}"
  renamed="${base/Limbo-release/Limbo-$VERSION}"
  [[ "$base" == "$renamed" ]] || mv "$apk" "$RELEASE_DIR/$renamed"
done

# A single file that runs everywhere is labelled as such, so nobody wonders which variant to grab.
if [[ -f "$RELEASE_DIR/Limbo-$VERSION.apk" ]]; then
  mv "$RELEASE_DIR/Limbo-$VERSION.apk" "$RELEASE_DIR/Limbo-$VERSION-universal.apk"
fi

shopt -s nullglob
FILES=("$RELEASE_DIR"/*.apk)
shopt -u nullglob

# ------------------------------------------------------------------ checksums and notes
( cd "$RELEASE_DIR" && sha256sum *.apk > SHA256SUMS )

if [[ -n "$NOTES_FILE" ]]; then
  cp "$NOTES_FILE" "$RELEASE_DIR/NOTES.md"
else
  cat > "$RELEASE_DIR/NOTES.md" <<EOF
## Limbo Keyboard $VERSION

$(git log --pretty=format:'- %s' "$(git describe --tags --abbrev=0 2>/dev/null || git rev-list --max-parents=0 HEAD)"..HEAD | head -40)

### Files

$(for apk in "${FILES[@]}"; do printf -- '- \`%s\` — %s\n' "${apk##*/}" "$(du -h "$apk" | cut -f1)"; done)

Requires Android 8.0 (API 26) or newer. Install with \`adb install -r <file>.apk\`.
EOF
fi

# Stage the files that the release page serves. They are committed on purpose: the workflow in
# .github/workflows/release.yml only uploads what is already in the tree, so the published checksum
# always matches a file anyone can inspect.
STAGE="$ROOT/releases/$VERSION"
mkdir -p "$STAGE"
rm -f "$STAGE"/*.apk "$STAGE"/SHA256SUMS
cp "${FILES[@]}" "$STAGE"/
cp "$RELEASE_DIR/NOTES.md" "$STAGE/NOTES.md"
( cd "$STAGE" && sha256sum ./*.apk > SHA256SUMS )

ok "Artefacts ready in dist/release/$VERSION"
ok "Staged releases/$VERSION — commit it before publishing"

if [[ "$PUBLISH" != "1" ]]; then
  exit 0
fi

command -v gh >/dev/null || fail "gh is not installed; use --no-publish or install the GitHub CLI"

TAG="v$VERSION"
TITLE="Limbo Keyboard $VERSION"
if [[ -z "$(git status --porcelain -- "releases/$VERSION")" ]]; then
  :
else
  fail "releases/$VERSION has uncommitted changes — commit and push it first"
fi
if [[ -n "$TARGET" ]]; then
  git rev-parse --verify "$TARGET" >/dev/null || fail "Unknown target $TARGET"
fi

ARGS=("$TAG" --title "$TITLE" --notes-file "$STAGE/NOTES.md")
if [[ -n "$TARGET" ]]; then ARGS+=(--target "$TARGET"); fi
if [[ "$DRAFT" == "1" ]]; then ARGS+=(--draft); fi

log "Creating GitHub release $TAG"
gh release create "${ARGS[@]}" || fail "Could not create the release"

# Asset uploads go through uploads.github.com, which some sandboxes block. Pushing the tag through
# git is what makes GitHub run the workflow that attaches the files, so nudge it explicitly.
log "Asking the release workflow to attach the APK"
if gh workflow run release.yml -f tag="$TAG" >/dev/null 2>&1; then
  ok "Workflow started — check it with: gh run list --workflow=release.yml"
else
  warn "Could not dispatch the workflow; run it manually: gh workflow run release.yml -f tag=$TAG"
fi
ok "Released $TAG"
