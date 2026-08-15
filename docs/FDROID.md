# F-Droid submission notes

What has to be true before opening the fdroiddata merge request, and the one complication
specific to this app.

## Prerequisites (in order)

1. A published (non-draft) GitHub release with tag `v1.0.0` and `versionCode = 1`.
2. The fastlane metadata in this repo (`fastlane/metadata/android/en-US/`) — F-Droid reads
   it automatically on every release. Screenshots go in
   `fastlane/metadata/android/en-US/images/phoneScreenshots/` (numbered PNGs); take them
   during device testing.
3. The build must succeed **with no keystore** — it does; the signing config tolerates
   absence and produces an unsigned APK, which F-Droid then signs with its own key.

## The complication: tesseract4android comes from JitPack

F-Droid disallows JitPack as a dependency source. The library publishes nowhere else, so
the fdroiddata recipe must build it from source and publish it to `mavenLocal` **under the
same coordinates** before building Tickbox — Tesseract4Android's own Gradle build supports
exactly this (`gradlew tesseract4android:publishToMavenLocal`), and `mavenLocal()` would
need to be added to the repositories in the recipe's `prebuild`. In fdroiddata metadata
terms, roughly:

```yaml
Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle: [yes]
    srclibs:
      - Tesseract4Android@4.9.0
    prebuild:
      - pushd $$Tesseract4Android$$ && gradle tesseract4android:publishToMavenLocal && popd
      - sed -i 's|url = uri("https://jitpack.io")|mavenLocal()|' ../settings.gradle.kts
```

(Illustrative, not final — the srclib definition for Tesseract4Android needs to exist or
be added in fdroiddata's `srclibs/`, its NDK requirements pinned, and the sed adjusted to
whatever the file looks like at that tag. Expect the reviewer to have opinions; other
apps in fdroiddata already build Tesseract4Android from source, so precedent exists.)

The alternative if that fight isn't worth it: ship the F-Droid build without OCR by
returning null from `AppContainer.textRecognizer` in a build variant. The UI already
hides every OCR affordance when no engine is present — that was the seam's original
state — so it's a small, honest fallback. Decide after the reviewer responds, not before.

## The rest of the recipe

```yaml
Categories: [Writing]
License: GPL-3.0-or-later
SourceCode: https://github.com/TheAmericanMaker/Tickbox
IssueTracker: https://github.com/TheAmericanMaker/Tickbox/issues
Changelog: https://github.com/TheAmericanMaker/Tickbox/blob/main/CHANGELOG.md
RepoType: git
Repo: https://github.com/TheAmericanMaker/Tickbox.git
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

`UpdateCheckMode: Tags` means future releases are picked up from git tags with no further
merge requests, as long as tags stay `v<versionName>` and versionCode increments.

Run `fdroid lint` and `fdroid build -v -l com.theamericanmaker.tickbox` locally (there is
a Docker image, `registry.gitlab.com/fdroid/fdroidserver:buildserver`) before submitting.
Expect one to four weeks of review.

## Tell users about the signature split

F-Droid signs with its key; GitHub Releases with ours. The two installs cannot upgrade
each other — switching sources means uninstalling, which deletes all notes (export
first!). The README should carry this warning next to the download links once both
channels exist. Bit-for-bit reproducible builds (so F-Droid publishes our signature)
are a worthwhile later project, not a 1.0 concern.
