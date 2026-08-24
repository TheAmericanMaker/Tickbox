# F-Droid submission notes

What has to be true before opening the fdroiddata merge request, and what to expect a reviewer
to ask about.

## The keystore is not a prerequisite

F-Droid builds from source and signs with **its own** key, so the release keystore gates GitHub
Releases and direct installs — not this. The signing config already tolerates having no keystore
and produces an unsigned APK, which is exactly what F-Droid wants.

## Prerequisites (in order)

1. A tag `v1.0.0` matching `versionName`, at `versionCode = 1`.
   Note `release.yml` triggers on `v*` and **exits 1 without `KEYSTORE_BASE64`**, so tagging
   before the keystore exists produces a failed workflow run. Either set the secret first or
   guard that workflow to skip when it is absent.
2. The fastlane metadata in this repo (`fastlane/metadata/android/en-US/`) — F-Droid reads it
   automatically on every release: title, descriptions, `changelogs/<versionCode>.txt`, `icon.png`
   and the numbered PNGs in `images/phoneScreenshots/`. Already present.
3. Nothing else. The build needs no secrets and no network beyond dependency resolution.

## JitPack is fine

`tesseract4android` publishes only to JitPack, and **that is acceptable.** F-Droid's
[inclusion policy](https://f-droid.org/en/docs/Inclusion_Policy/) lists JitPack.io among the
trusted Maven repositories, alongside Maven Central, Google Maven, OSS Sonatype, OSS JFrog and
Clojars. No source build of Tesseract4Android and no `srclibs` entry is required.

An earlier version of this document claimed the opposite and described an elaborate
`publishToMavenLocal` workaround. That was wrong, and it was treated as the largest obstacle to
publishing for some time. Verify a claim like that against the policy before building a plan on it.

## Build one APK, not five

ABI splits are on by default, which emits five APKs per variant — good for downloads, awkward for
a recipe that expects one artifact. Pass `abiSplits=false` and the build produces a single APK
carrying all four ABIs at the base `versionCode`:

```
app/build/outputs/apk/withOcr/release/app-withOcr-release-unsigned.apk    31.0 MB, versionCode 1
```

Both routes are verified locally. The property is read with `findProperty`, so either a
command-line flag or a line in `gradle.properties` works — the recipe uses the latter, since
fdroidserver has no field for extra Gradle arguments:

```yaml
prebuild: echo "abiSplits=false" >> ../gradle.properties
```

(`fdroid rewritemeta` normalises a single command to that scalar form.)

Per-ABI APKs are a later refinement: the `versionCode` offsets already exist for it (arm64-v8a
4001, x86_64 3001, x86 2001, armeabi-v7a 1001), so it becomes four `Builds` entries when it is
worth the extra review surface. Submit one APK first.

## The recipe

`metadata/com.theamericanmaker.tickbox.yml` in a fork of
[fdroiddata](https://gitlab.com/fdroid/fdroiddata), on a branch named after the application id:

```yaml
Categories:
  - Writing
License: GPL-3.0-or-later
AuthorName: James Sesler
SourceCode: https://github.com/TheAmericanMaker/Tickbox
IssueTracker: https://github.com/TheAmericanMaker/Tickbox/issues
Changelog: https://github.com/TheAmericanMaker/Tickbox/blob/HEAD/CHANGELOG.md

RepoType: git
Repo: https://github.com/TheAmericanMaker/Tickbox.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle:
      - withOcr
    prebuild: echo "abiSplits=false" >> ../gradle.properties

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

`gradle: [withOcr]` selects the flavour — the unqualified `yes` that used to be here predates the
flavour split and would no longer resolve. `noOcr` is deliberately not submitted; one variant is
enough for a first review, and the small build is easy to add later as a second package if it ever
earns its own listing.

**This recipe has been built locally and it works.** `fdroid build` succeeded with scanning
enabled, from commit `2651bb2`, producing one unsigned APK at `versionCode 1` / `versionName
1.0.0` carrying all four ABIs. The `Builds` block is no longer a guess.

Submit with `commit: v1.0.0` once that tag exists. The local runs used a branch SHA because there
is no tag yet, which validates everything except the tag resolving.

## Testing it locally

Two corrections to the obvious route, both of which cost time.

**The buildserver container does not contain `fdroid`.**
`registry.gitlab.com/fdroid/fdroidserver:buildserver` exists, but it holds the Android SDK, NDK,
Gradle and a JDK under `/opt` plus provisioning scripts — it is the environment a build *runs in*,
not the tool that drives one. `which fdroid` returns nothing. An earlier version of this document
said to run `fdroid lint` and `fdroid build` inside it, which cannot work.

**`fdroidserver` from pip is missing a helper.** Install it in a virtualenv, then fetch
`gradlew-fdroid` by hand — the package expects it in `site-packages` but does not ship it, and in
its own repository that filename is a *symlink*, so fetching the raw path gives you ten bytes of
the string `gradlew.py`:

```bash
python3 -m venv fdroid-venv
./fdroid-venv/bin/pip install fdroidserver
SP=$(echo ./fdroid-venv/lib/python3*/site-packages)
curl -fsSL -o "$SP/gradlew-fdroid" https://gitlab.com/fdroid/gradlew-fdroid/-/raw/main/gradlew.py
chmod +x "$SP/gradlew-fdroid"
```

Then from a clone of fdroiddata with the metadata file in place:

```bash
export ANDROID_HOME=$HOME/Android/Sdk JAVA_HOME=$HOME/.jdks/jdk-17.0.20+8
FD=../fdroid-venv/bin/fdroid
$FD lint com.theamericanmaker.tickbox
$FD rewritemeta com.theamericanmaker.tickbox
$FD build com.theamericanmaker.tickbox -v --no-tarball
```

`fdroid build` skips a version it has already built, so delete
`unsigned/com.theamericanmaker.tickbox_1.apk` between attempts. Do **not** reach for `--force` to
get past that — it also carries on regardless of scan problems, which is the one thing you want the
run to tell you about.

`readmeta` over the whole repository currently fails on an unrelated app's Bitcoin address. That is
upstream's data, not a signal about this recipe; `lint` on a single app is the useful check.

### What the local runs found

- **`lint`**: `Changelog` pointed at `/blob/main/`; it wants `/blob/HEAD/`. Fixed above.
- **`rewritemeta`**: normalises a single `prebuild` command from a list to a scalar. Commit what it
  produces rather than fighting the formatter.
- **The scanner rejected the build outright** over `org.gradle.toolchains.foojay-resolver` in
  `settings.gradle.kts` — a "usual suspect", because it downloads a JDK during configuration and a
  build that fetches its own toolchain is not reproducible. The plugin is now removed and
  `jvmToolchain(17)` resolves from a locally installed JDK 17 instead. **Do not add a toolchain
  resolver back.**
- Benign: fdroid strips the committed `gradle-wrapper.jar` and supplies its own Gradle, picking
  8.13 from the wrapper's `distributionUrl` — so keep `gradle-wrapper.properties` accurate. It also
  warns that it cannot strip the Tesseract and Leptonica `.so` files, which is a warning only.
- Confirms the no-keystore claim at the top of this file: the log says
  `Cleaned build.gradle.kts of keysigning configs`.

## Submitting

```bash
git add metadata/com.theamericanmaker.tickbox.yml
git commit -m "New App: com.theamericanmaker.tickbox"
git push origin com.theamericanmaker.tickbox
```

and open the merge request with that branch as the source. Expect one to four weeks of review.

## What a reviewer is most likely to ask about

**The bundled language model.** `app/src/withOcr/assets/tessdata/eng.traineddata` is the only
binary in the tree that is not build tooling — 4113088 bytes, sha256
`7d4322bd2a7749724879683fc3912cb542f19906c83bcc1a52132556427170b2`, taken from
[tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) under Apache-2.0. It is data
rather than executable code, and OCR apps on F-Droid do ship it, but the policy's wording about
binary dependencies invites the question. The checksum is recorded in `README.md` and `CLAUDE.md`
so it can be verified against upstream rather than taken on trust; keep it current if the model is
ever replaced.

**Anti-features: none apply.** No advertising, no tracking, no non-free dependencies, and no
`INTERNET` permission at all — the only permission in the manifest is `CAMERA`. Worth saying
explicitly in the merge request.

## Tell users about the signature split

F-Droid signs with its key; GitHub Releases with ours. The two installs cannot upgrade each other,
so switching sources means uninstalling, which deletes all notes — export first. The README should
carry that warning next to the download links once both channels exist.

Bit-for-bit reproducible builds, so F-Droid publishes *our* signature and the two become
interchangeable, are a worthwhile later project rather than a 1.0 concern.
