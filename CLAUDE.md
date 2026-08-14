# Working on Tickbox

Notes for anyone — human or agent — picking this repo up.

## What this is

A local-first Android notes and checklists app, extracted from the notepad feature of
[Smart Toolkit](https://github.com/TheAmericanMaker/Smart-Toolkit), a 21-tool utility app whose
owner only ever used the notepad. Tickbox is that feature as a standalone, GPL-3.0 app.

The full extraction plan, including the remaining roadmap, is in
[docs/EXTRACTION_PLAN.md](docs/EXTRACTION_PLAN.md).

## Read this before trusting the build

**CI proves the project compiles, lints, and packages. It does not prove the app works.**
As of the extraction being complete, **nothing has ever run on a device or emulator**, and there
are **no tests** — `testDebugUnitTest` passes vacuously because no test source exists yet. Do not
read a green build as working software.

The extraction was done in an environment with no Android SDK and no access to `dl.google.com`, so
GitHub Actions was the only compiler available. That constraint does not apply on a normal machine:
build and run locally, and prefer doing so over trusting CI.

The highest-value work available right now is [docs/VERIFICATION.md](docs/VERIFICATION.md) — the
device checklist covering what the compiler cannot see.

## Commands

```bash
./gradlew assembleDebug        # debug APK -> app/build/outputs/apk/debug/
./gradlew testDebugUnitTest    # no tests exist yet; passes vacuously
./gradlew lintDebug            # Android lint
./gradlew assembleRelease      # minified; UNSIGNED (no signing config yet — Phase 10)
```

Requires **JDK 17** and **Android SDK Platform 35**. The Gradle toolchain resolver will fetch a
JDK if the one on PATH is wrong.

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

- **No OCR engine.** `TextRecognizer` returns null from the container, so the extract-text
  affordance is hidden. Smart Toolkit used Google ML Kit, which is proprietary and would make the
  app ineligible for F-Droid. Tesseract (`tesseract4android`) is the intended replacement — Phase 6
  — and it needs a quality spike on real photos before being committed to.
- **No drag-to-reorder.** The original rendered a drag handle with no gesture attached, which
  TalkBack announced as a reorder affordance that did not exist; that handle was removed. Real
  reordering is not a wiring job: the checklist renders as two filtered sections (unchecked, then
  checked) whose display order does not match the underlying list indices, so a drag between
  visible neighbours can map to non-adjacent items. It needs those sections remodelled.
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
