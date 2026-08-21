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
./gradlew assembleWithOcrDebug   # the full app  -> app/build/outputs/apk/withOcr/debug/
./gradlew assembleNoOcrDebug     # the small app -> app/build/outputs/apk/noOcr/debug/
./gradlew installWithOcrDebug    # picks the right ABI for the attached device
./gradlew testWithOcrDebugUnitTest testNoOcrDebugUnitTest   # JVM only, no emulator
./gradlew lintWithOcrDebug lintNoOcrDebug                   # Android lint
./gradlew ktlintCheck            # style (advisory in CI until an initial ktlintFormat commit)
./gradlew assembleRelease        # both flavours, minified; signed only with a keystore
```

**There are two product flavours, so the unqualified task names no longer exist.**
`assembleDebug` and `assembleRelease` survive as aggregates over both flavours, but
`testDebugUnitTest` and `lintDebug` are gone — Gradle fails with "task not found" rather than
doing something reasonable. The variant names are `withOcr` / `noOcr`; see the OCR entry under
*Known gaps* for what differs.

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
ocr/             TextRecognizer seam, shared by both flavours
ui/list/         note list screen + ViewModel
ui/edit/         editor screen + ViewModel, checklist row, images, templates, categorizer
ui/theme/        TickboxTheme

src/withOcr/     OcrBuild + TesseractTextRecognizer + the 4 MB model in assets
src/noOcr/       OcrBuild, returning null — the app's own behaviour before OCR existed
src/testWithOcr/ tests that reach inside TesseractTextRecognizer
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
  both run and the better result wins, **scored by how many words Tesseract read *and was confident
  of*** — `wordConfidences()` counted above a threshold.

  Two earlier metrics failed here in opposite directions, and #38 is the measurement.
  `meanConfidence()` rewards reading *less*: on the packing slip, Otsu returned 11 characters at
  confidence 93 while Sauvola returned the whole address at 85. Counting word-*shaped* tokens
  rewards reading *more*, including nonsense: on a screenshot of this app, Otsu read the screen
  almost perfectly and scored 8, while Sauvola shredded the glyphs into 25 word-shaped fragments
  and won — noise makes more tokens than clean text does.

  Note that "Sauvola is worse on photos of screens" is the wrong frame for the common case. A
  *screenshot* is a pristine image where Otsu wins easily; it was screenshots, not photos of
  screens, that the old scoring got wrong. See `TesseractTextRecognizer`, `PassScoringTest` which
  pins both measurements, and `docs/VERIFICATION.md` section J.
- **The 4 MB `tessdata_fast` model is deliberate and sufficient.** The 15 MB `tessdata_best` was
  measured against it on the failing image and was **byte-identical** until the thresholding was
  fixed, after which it corrected exactly one digit. Not worth 11 MB. Note the dependency comes
  from **JitPack** (content-filtered to that one group in `settings.gradle.kts`) because the
  library publishes nowhere else; F-Droid disallows JitPack, so the eventual fdroiddata recipe must
  build the library from source (`publishToMavenLocal`, same coordinates).
- **OCR is a build flavour, and a runtime download is not an option** (#31). `withOcr` is the app
  as it has been; `noOcr` drops Tesseract, Leptonica and the model. Measured on release builds:
  **31.0 MB against 1.4 MB**, because the app's own code is all that is left once ~27 MB of native
  libraries and a 4 MB model go — and R8 shrinks bytecode, not native code.

  The obvious alternative — a button in settings that fetches OCR on first use — cannot be built.
  Android 10 forbids executing code from the app's data directory (W^X), so native libraries have
  to arrive through the package manager. The model alone *is* downloadable, but it is 3.9 MB of the
  31.5 MB and fetching it would cost the app its "no network permission" claim. Play Feature
  Delivery does deliver native code on demand and is disqualified for needing Play Core, which is
  what ruled out ML Kit in the first place.

  The whole difference is `OcrBuild`, which exists once per flavour and holds two facts that must
  agree: whether an engine exists, and whether the UI may advertise one. Keeping them in one object
  is deliberate — split them and you eventually ship a build that hides the button but carries
  31 MB, or one that offers extraction it cannot do.
- **One APK per ABI, and the `versionCode`s must differ** (#30). Tesseract's native libraries are
  ~27 MB across four architectures and R8 cannot shrink them, so a single APK made every phone
  carry three it cannot run. Measured on release builds:

  | | universal | arm64-v8a |
  | --- | --- | --- |
  | `withOcr` | 31.0 MB | **10.5 MB** |
  | `noOcr` | 1.5 MB | 1.4 MB |

  `noOcr` barely moves, because it has almost no native code — which is why only its universal
  APK is published.

  The `versionCode` override in `androidComponents.onVariants` is load-bearing, not tidiness.
  Splits that share a code are indistinguishable to F-Droid and to the updater, and the order
  matters too: a device installs the highest code it can run, so `arm64-v8a` (4001) has to
  outrank `armeabi-v7a` (1001) or a modern phone takes the 32-bit build. The universal APK keeps
  the base code so anything more specific wins.

  `x86`/`x86_64` are built but not published — emulator-only for a phone app. They are built
  rather than dropped because this project has never been run on an emulator, and that is
  precisely where CI cannot help.
- **Drag-to-reorder works by key, not index.** The checklist renders as two filtered sections, so
  display position ≠ list index; `onReorderChecklistItems` takes tempIds for that reason. Don't
  "simplify" it back to indices — that reintroduces the bug that kept this feature out of the
  original app.
- **Smart Toolkit migration testing was dropped by the owner** (one user, grocery lists). The
  archive format compatibility is still maintained and tested — `NoteImportArchiveTest` and
  `BackupRoundTripTest` pin it — but nobody is expected to run a real Smart Toolkit export
  through it before release.
- **Indent is capped at one level**, matching the original.
- **Indent is a gesture, not a button — don't add the buttons back.** Drag an item's tick box
  sideways: right indents, left outdents, tap still ticks. Buttons were tried twice and failed
  twice. Permanently visible, they spent a fixed slice of every row on a one-level feature, and
  the owner reported lists as unreadably crowded. Revealed on focus, they were worse: tapping into
  a row reflowed its text from 513px to 356px, so the line re-wrapped under the finger that was
  trying to edit it. A gesture has no width and cannot do that. It is undiscoverable on its own,
  which a one-time hint should fix — but discoverability is a cheaper problem than a layout that
  moves while you use it.
- **Any control that appears on focus must reserve its width when hidden.** The delete `×` is
  composed on every row and merely made transparent, disabled and semantics-free when the row is
  not focused. Wrapping it in `if (isFocused)` instead is what caused the reflow above.
- **Search is a `LIKE` scan.** Fine to roughly a thousand notes. FTS when someone measures a
  problem, not before.
- **Checklist suggestions were removed, deliberately** (#27). They were a hardcoded English
  keyword map offering generic filler — titling a list `tasks` produced "Review emails",
  "Prepare report" — and across daily use they were never once useful. They also occupied about a
  third of the editor and never scrolled away (#26). The primary input here is dictation, which is
  faster than tapping a chip and is not limited to words the map knows, so the feature was saving
  effort on an interaction nobody was having. Do not reintroduce a fixed suggestion list. A version
  drawing on the user's *own* previous items is a legitimate future feature and would share no code
  with what was deleted.
- **`NoteCategorizer` is a hardcoded English keyword map**, kept so the extraction stayed
  behaviour-preserving. It survives the above for two reasons: it costs no screen space, and it
  also owns `noteColors` / `getNoteColor`, which the colour-label picker and the list cards both
  depend on — so removing the categorising half means untangling that first. Real user-editable
  tags remain the planned direction.

## Scope

Tickbox is meant to stay small: the notes app you would recommend to someone who wants Google Keep
without an account. Some things are permanently out of scope, and saying so up front is cheaper
than arguing later — markdown and rich text, reminders and notifications, sync, accounts, cloud,
and collaboration. The ZIP export is the sync story. See the plan for the reasoning.
