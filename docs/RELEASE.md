# Releases

Limbo Keyboard ships as a plain, signed APK. There is no Play-only dependency, no beta channel and
no dynamic delivery: the file you download works on any Android 8.0+ device.

## Why the release has one APK, not four

The keyboard contains **no native code**. Everything — layout, rendering, springs, clipboard,
dictionary — is Kotlin compiled to a single `classes.dex`, and DEX is architecture independent.
Separate `arm64-v8a` / `armeabi-v7a` / `x86_64` APKs would therefore be byte-identical copies of
one another, and shipping four identical files only makes the download page confusing.

The universal APK is the honest artefact today:

| Artefact | Contains | When to use |
|---|---|---|
| `Limbo-<version>-universal.apk` | shared DEX + resources | always |

The moment the project gains an `.so` (a gesture engine, a native decoder), the same scripts start
producing real splits without any edits:

- `./toolchain/build.sh --release` detects `lib/<abi>/` in the package and emits
  `Limbo-<version>-arm64-v8a.apk`, `…-armeabi-v7a.apk`, `…-x86_64.apk` **plus** the universal APK.
- `./gradlew bundleRelease` (with `LIMBO_ABI_SPLITS=1` or an App Bundle upload) lets Play and
  `bundletool` generate per-device splits.
- `app/build.gradle.kts` already carries the `splits { abi { … } }` block, disabled and documented.

## Cutting a release

```bash
./toolchain/release.sh                    # next patch version, build, stage, publish
./toolchain/release.sh --version 1.2.0    # explicit version
./toolchain/release.sh --no-bump          # rebuild the current version
./toolchain/release.sh --no-publish       # stop after staging into releases/<version>
```

The script bumps `versionName` and `versionCode` in both build paths, builds a signed APK, writes
`SHA256SUMS` and `NOTES.md`, copies them into `releases/<version>/` and creates the GitHub release.

**Commit `releases/<version>/` and push the tag** — the release page gets its files from the tree:

```bash
git add releases/1.0.1 && git commit -m "Release 1.0.1" && git push
git tag v1.0.1 && git push origin v1.0.1      # triggers .github/workflows/release.yml
```

The workflow checks `SHA256SUMS` and attaches the APK to the release. Keeping the binary in the
repository is deliberate: the published file and the committed one are the same bytes, so anyone can
verify the checksum without trusting a build machine.

If a release was created without its files (an upload can fail on a restricted network), re-run the
workflow from the Actions tab, or dispatch it:

```bash
gh workflow run release.yml -f tag=v1.0.1
```

## Signing

Never commit a keystore. The default release key lives in `toolchain/.keys/limbo-release.jks`
(ignored by git) and is only meant for local builds and previews.

For a real release, provide your own key through the environment:

```bash
export LIMBO_KEYSTORE=/secure/limbo-release.jks
export LIMBO_STORE_PASSWORD=…
export LIMBO_KEY_ALIAS=limbo
export LIMBO_KEY_PASSWORD=…
./toolchain/release.sh --version 1.1.0
```

The Gradle path reads the same variables. In CI, keep them as repository secrets
(`KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and decode the keystore before
building — `.github/workflows/release.yml` does exactly that.

**Back the keystore up.** Android refuses to update an installed app whose signing key changed, so
losing the key means users have to uninstall and reinstall.

## Manual checks before publishing

```bash
apksigner verify --print-certs dist/Limbo-release.apk   # v2/v3 schemes, one signer
aapt2 dump badging dist/Limbo-release.apk               # package, sdk levels, ime component
unzip -l dist/Limbo-release.apk | grep -c lib/           # 0 for a pure-Kotlin build
adb install -r dist/Limbo-release.apk
```
