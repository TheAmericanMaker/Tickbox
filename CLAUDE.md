# Working on Tickbox

Notes for anyone — human or agent — picking this repo up.

## What this is

A local-first Android notes and checklists app, extracted from the notepad feature of
[Smart Toolkit](https://github.com/TheAmericanMaker/Smart-Toolkit), a 21-tool utility app whose
owner only ever used the notepad. Tickbox is that feature as a standalone, GPL-3.0 app.

The full extraction plan, including the remaining roadmap, is in
[docs/EXTRACTION_PLAN.md](docs/EXTRACTION_PLAN.md).

## Read this before trusting the build

**CI proves the project compiles, lints, passes the JVM test suite, and packages.** The suite
(`app/src/test`) covers the data layer, the backup format, the share formatter, and the editor's
state machine — all on the JVM via Robolectric, no emulator. What CI still cannot prove is
on-device behaviour: gestures, dictation, camera, OCR quality, R8. That lives in
[docs/VERIFICATION.md](docs/VERIFICATION.md), parts of which have been run on real hardware and
parts not — the checklist itself records which is which.

Much of the original code was written in an environment with no Android SDK and no access to
`dl.google.com`, where GitHub Actions was the only available compiler. That constraint does not
apply on a normal machine: build and run locally, and prefer doing so over trusting CI.

## Commands

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # the whole test suite; JVM only, no emulator
./gradlew lintDebug            # Android lint
./gradlew ktlintCheck          # style (advisory in CI until an initial ktlintFormat commit)
./gradlew assembleRelease      # minified; signed only if a keystore is configured
```

Requires **JDK 17** and **Android SDK Platform 35**.

**Kotlin 2.1.0 cannot run on Java 25**, and it says so in a way that gives nothing away:

```
* What went wrong:
25.0.4
```

That is Kotlin's bundled IntelliJ `JavaVersion.parse` refusing a version string it predates. It is
not AGP and it is not Gradle — Gradle 8.13 runs on Java 25 quite happily, which is what makes the
error so confusing. Note that `jvmToolchain(17)` and the foojay resolver do **not** rescue you:
they provision a JDK for *compilation*, while the JVM running the Gradle daemon is the one that
has to be old enough.

Current distros are dropping older JDKs — Fedora 44 packages nothing below 25 — so you may need a
JDK 17 from Adoptium. Keep it out of the repo: `JAVA_HOME` per shell, or `org.gradle.java.home` in
`~/.gradle/gradle.properties`, never the committed `gradle.properties`. Bumping Kotlin is the real
fix, but it drags `ksp` along in lockstep and deserves its own reviewed change.

Debug builds carry `applicationIdSuffix = ".debug"`, so a debug Tickbox installs alongside a
release one — and alongside Smart Toolkit, which is a different package entirely. That is
deliberate: it is how the data migration gets validated without risking the real notes.

## Architecture

Deliberately small. One Gradle module, one activity, all Compose.

- **DI is manual.** `AppContainer` holds the whole graph as four `by lazy` properties, built in
  `TickboxApp.onCreate`. ViewModels are constructed by `viewModelFactory { initializer { … } }`
  companions that reach the container through `CreationExtras.container`. There is no Hilt and no
  Dagger; do not reintroduce them. KSP exists solely for Room.
- **ViewModels never hold a `Context`.** File I/O goes through `NoteImageStore`, and `Uri` stream
  work through `NoteBackupManager`. Keep it that way — it is what makes them testable on the JVM.
- **Screens split stateful/stateless.** `NoteListScreen` delegates to `NoteListContent`, which
  takes plain state and callbacks. New screens should follow this: it is what lets previews and
  Compose tests run without an Application behind them.
- **Room starts at version 1** with schemas exported to `app/schemas/`. Commit the generated
  `1.json` on your first successful build if it is not already there.

```
data/            entities, DAOs, NoteDatabase, NoteRepository, NoteImageStore, preferences
data/backup/     ZIP export/import — see the format contract below
ocr/             TextRecognizer seam (no engine wired yet)
ui/list/         note list screen + ViewModel
ui/edit/         editor screen + ViewModel, checklist row, images, templates, categorizer
ui/theme/        TickboxTheme
```

## Things that will bite you

**The backup format is a compatibility contract, not an internal detail.** It is how the owner's
existing notes migrate out of Smart Toolkit. `NoteImportArchive.kt` and the JSON written by
`NoteBackupManager.buildExportJson` must stay readable by, and readable from, Smart Toolkit's
version. Adding a field is safe — every read uses an `opt*` accessor with a default. Renaming,
moving, restructuring the ZIP layout, or switching to kotlinx-serialization is not. `notes.json`
lives at the archive root, images are flat under `images/`, and there are no directory entries.

**`NoteImportArchive` is the only untrusted-input surface in the app.** It rejects path traversal,
absolute paths, backslashes, directory entries, duplicates, and oversized entries, and it stages to
a scratch directory before touching real storage. Treat changes to it as security-relevant. It was
carried over near-verbatim on purpose.

**Checklist item ids must survive a save.** `NoteRepository.saveNote` reconciles rows — updates
what still exists, inserts what is new, deletes the rest — rather than wiping and reinserting the
table on every autosave, which is what the original did. `NoteEditViewModel` carries ids into the
domain model and reads generated ids back afterwards. Break either half and the reconciliation
silently degrades to the old churn, which is invisible in the UI and only shows up as unstable ids.

**Importing the same archive twice duplicates every note.** `importNote` always inserts. This is
known and accepted for 1.0; a dedup key is a step toward sync, which is explicitly out of scope.

**The startup orphan sweep deletes files.** `TickboxApp` removes images in `note_images/` that no
note references. It skips files modified in the last 24 hours so it cannot race an import or a
just-attached photo. It has never actually run — watch it on first launch against a real library,
and check nothing legitimate disappears.

## Conventions

- SPDX headers on every `.kt` file: `// SPDX-FileCopyrightText:` then
  `// SPDX-License-Identifier: GPL-3.0-or-later`.
- Trailing commas, 120-column lines, 4-space indent. See `.editorconfig`.
- ktlint is planned but **not wired up yet** (Phase 8). When adding it, run `ktlintFormat` as its
  own commit so the reformat does not bury real changes, and keep
  `ktlint_function_naming_ignore_when_annotated_with = Composable` in `.editorconfig` or every
  `@Composable fun NoteListScreen()` gets flagged.
- User-facing strings are still inline rather than in `strings.xml`. Extracting them is a planned
  1.1 task that also gates localisation; do not do it piecemeal.
- CI uses `concurrency: cancel-in-progress`, so pushing twice quickly cancels the first run. A
  `cancelled` conclusion usually means superseded, not broken.

## Known gaps, deliberate

These are decisions, not oversights. Check the plan before "fixing" them.

- **OCR runs two passes and keeps the better one — don't "simplify" it to one.** Tesseract's own
  global Otsu threshold assumes a single exposure across the frame, which a photo of paper does not
  have: on a half-shadowed packing slip it binarised the page into a black region and a white blob
  and returned `Mid Michiga` from a clean four-line address. Sauvola local thresholding fixes that
  completely, **but is worse on photos of screens**, where it mangles moiré and anti-aliasing. So
  both run and the output with more word-like tokens wins. The scoring is deliberately not
  Tesseract's own confidence — `meanConfidence()` rewards reading *less*, and `wordConfidences()`
  counts internal blobs that never reach the text. See `TesseractTextRecognizer.binarise` and
  `ReadableWordCountTest`; the reasoning is in `docs/VERIFICATION.md` section J.
- **The 4 MB `tessdata_fast` model is deliberate and sufficient.** The 15 MB `tessdata_best` was
  measured against it on the failing image and was **byte-identical** until the thresholding was
  fixed, after which it corrected exactly one digit. Not worth 11 MB. Note the dependency comes
  from **JitPack** (content-filtered to that one group in `settings.gradle.kts`) because the
  library publishes nowhere else; F-Droid disallows JitPack, so the eventual fdroiddata recipe must
  build the library from source (`publishToMavenLocal`, same coordinates).
- **Drag-to-reorder works by key, not index.** The checklist renders as two filtered sections, so
  display position ≠ list index; `onReorderChecklistItems` takes tempIds for that reason. Don't
  "simplify" it back to indices — that reintroduces the bug that kept this feature out of the
  original app.
- **Smart Toolkit migration testing was dropped by the owner** (one user, grocery lists). The
  archive format compatibility is still maintained and tested — `NoteImportArchiveTest` and
  `BackupRoundTripTest` pin it — but nobody is expected to run a real Smart Toolkit export
  through it before release.
- **Indent is capped at one level**, matching the original.
- **24dp touch targets** on the indent/outdent buttons, half Android's 48dp minimum. Part of the
  planned accessibility pass, not a quick fix — it changes layout density.
- **Search is a `LIKE` scan.** Fine to roughly a thousand notes. FTS when someone measures a
  problem, not before.
- **`NoteCategorizer` and `ChecklistSuggestionProvider` are hardcoded English keyword maps.** They
  were kept so the extraction stayed behaviour-preserving. Replacing them with real user-editable
  tags is the planned 1.2 direction.

## Scope

Tickbox is meant to stay small: the notes app you would recommend to someone who wants Google Keep
without an account. Some things are permanently out of scope, and saying so up front is cheaper
than arguing later — markdown and rich text, reminders and notifications, sync, accounts, cloud,
and collaboration. The ZIP export is the sync story. See the plan for the reasoning.
