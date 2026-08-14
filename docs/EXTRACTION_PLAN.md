# Extract the Notepad into **Tickbox** — a standalone open-source notes app

## Context

`Smart-Toolkit` is a native Android app (Kotlin + Jetpack Compose) bundling 21 utilities. In
practice only one gets used: the Notepad. It is also, by a wide margin, the most developed feature
in the repo — `PLAN.md:3` calls it "the gold standard," and the other 20 tools were built by copying
its patterns.

Carrying 20 unused tools has real cost. The manifest requests `CAMERA`, `RECORD_AUDIO`, `INTERNET`,
network state, `VIBRATE`, `POST_NOTIFICATIONS` and four `FOREGROUND_SERVICE` permissions, and
declares four foreground services. The dependency graph pulls in CameraX, ML Kit barcode, ZXing and
Guava. One Room database mixes note tables with calculator history; one DataStore file mixes note
flags with stopwatch laps and tally counters. None of that serves a note app, and all of it works
against the two goals: a **simple, polished** app and a **credible open-source project**.

Exploration confirmed the cut is clean. `feature/notepad/` imports **zero** other `feature/*`
package. Its only foreign imports are `ui.components.UtilityTopBar`, `data.model.*`,
`data.repository.NoteRepository`, `data.db.*`, and `data.preferences.UserPreferencesRepository`.
No feature flags, global singletons, event bus, auth, analytics, ads or billing. About **3,300
lines** of note-specific Kotlin move over nearly verbatim.

**Outcome:** a new public repo `TheAmericanMaker/Tickbox` — a GPL-3.0, local-first, offline Android
notes-and-checklists app, shipped as signed APKs on GitHub Releases and submitted to F-Droid, with
existing Smart Toolkit notes migrated through the ZIP backup that already exists.

## Decisions

| Decision | Choice |
|---|---|
| Platform | Android-native, Kotlin + Compose — **simplified stack** (Hilt removed) |
| Repo | Brand-new standalone repo, clean history. Smart-Toolkit untouched. |
| Name | **Tickbox** |
| Package / namespace / applicationId | `com.theamericanmaker.tickbox` |
| DB filename | `tickbox.db` |
| License | **GPL-3.0-or-later** |
| Distribution | GitHub Releases (CI-signed APK) **+ F-Droid** |
| OCR | **tesseract4android replaces ML Kit** — ML Kit is proprietary and blocks F-Droid |
| v1.0 scope | **Extract + fix + ship.** Feature polish is 1.1+ |
| Existing notes | Migrate via the existing ZIP export/import |

### One concern to state up front

Tesseract is a real step down from ML Kit on **photos** (as opposed to flat scans) — it wants
high-contrast, deskewed, ~300 DPI input, and without a preprocessing pipeline the results on a
handheld snapshot of a receipt will be noticeably worse. Language data is also a size decision:
`tessdata_fast/eng` is ~2 MB with reduced accuracy, standard `tessdata/eng` is ~15 MB.

Proceeding as chosen, with a **timeboxed spike in Phase 6**: integrate Tesseract, test it on ten
real photos of the kind actually used. If quality is unacceptable, the fallback is to **drop OCR
from 1.0**, not to reintroduce ML Kit — either path keeps F-Droid clean, and OCR can return later.
Flagging so the decision point is scheduled rather than discovered late.

---

## Phase 0 — Prepare (no code)

1. **Export the real Smart Toolkit notes twice, to two different destinations** (cloud + PC). Do
   this before anything else — it is the only copy of the data.
2. **Check the export against the importer's limits**, which `NoteImportArchive` enforces:
   `notes.json` ≤ 2 MB, each image ≤ 10 MB, **whole archive ≤ 64 MB**, ≤ 200 images total, ≤ 5 per
   note. A heavily photo-attached library can exceed 64 MB, and the failure surfaces as a generic
   "Backup file is too large." If over, raise `MAX_IMPORT_TOTAL_BYTES` in Tickbox — it is a
   client-side sanity limit, not the security boundary (the per-entry limits stop zip bombs).
3. Build a trimmed fixture ZIP from that export for the regression test: one text note, one
   checklist with indented + checked items, one pinned note with a colour label, one note with two
   images, one note with unicode/emoji in the title.
4. Create `TheAmericanMaker/Tickbox` (public) on GitHub. *This session's GitHub scope is limited to
   `theamericanmaker/smart-toolkit`; creating the new repo may need manual creation or an access
   grant. Verify before assuming.*
5. Generate the release keystore (**≥25-year validity**), back it up in two places, add the four
   GitHub secrets. Losing this key means every user must uninstall to ever update again.
6. Commit this plan to the Smart-Toolkit branch `claude/note-app-extraction-plan-2ry2y7` as
   `docs/TICKBOX_EXTRACTION.md` — that branch exists for exactly this.

**Done when:** two verified off-device exports, fixture built, repo created, keystore backed up.

---

## Phase 1 — Scaffolding that builds

Copy verbatim: `gradlew`, `gradlew.bat`, `gradle/wrapper/` (Gradle 8.13), `.gitignore` (already
excludes `*.keystore`, `*.jks`, `keystore.properties`).

**`gradle/libs.versions.toml`** — keep agp 8.13.2, kotlin 2.1.0, ksp 2.1.0-1.0.29 (Room still needs
it), compose-bom 2024.12.01, room 2.6.1, datastore 1.1.1, core-ktx 1.15.0, lifecycle 2.8.7,
activity-compose 1.9.3, navigation-compose 2.8.5, coroutines 1.9.0, junit 4.13.2.
**Drop 11 libraries:** hilt, hilt-navigation-compose, camerax ×4, mlkit-barcode,
mlkit-text-recognition, concurrent-futures ×2, guava, zxing-core, plus the dead `play-services-ads`
and `billing` catalog entries (declared but never in `dependencies {}` — only stale ProGuard rules).
CameraX is safe to drop because photo capture goes through `ActivityResultContracts.TakePicture()`
+ FileProvider, not a CameraX preview (`NoteEditScreen.kt:187-221`).

**`app/build.gradle.kts`** — four plugins (`android.application`, `kotlin.android`,
`kotlin.compose`, `ksp`), namespace + applicationId `com.theamericanmaker.tickbox`, compileSdk 35,
minSdk 26, targetSdk 35, versionCode 1, versionName "1.0.0", JDK 17. Add:

```kotlin
defaultConfig { ksp { arg("room.schemaLocation", "$projectDir/schemas") } }
testOptions { unitTests.isIncludeAndroidResources = true }
buildTypes { debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" } }
```

`applicationIdSuffix = ".debug"` matters immediately — it lets a debug Tickbox sit alongside a
release install while validating the data migration.

**`AndroidManifest.xml`** — only `CAMERA`, plus optional camera/microphone `<uses-feature>`. Keep
the `FileProvider` (authority `${applicationId}.fileprovider`), `allowBackup`,
`dataExtractionRules`, `fullBackupContent`, `enableOnBackInvokedCallback`, `supportsRtl`. Drop
`RECORD_AUDIO`, `INTERNET`, `ACCESS_WIFI_STATE`, `ACCESS_NETWORK_STATE`, `VIBRATE`,
`POST_NOTIFICATIONS`, both `FOREGROUND_SERVICE*`, all four `<service>` blocks, the four irrelevant
`<uses-feature>` entries, and `launchMode="singleTask"` (that existed for cross-tool intent
routing). Declaring zero network permissions is what makes the privacy claim **verifiable from the
manifest** — a selling point, not just hygiene.

**`res/`** — carry `xml/file_paths.xml`, `xml/backup_rules.xml`, `xml/data_extraction_rules.xml`
verbatim (all three are already note-shaped). New `values/strings.xml` (`app_name` = Tickbox),
`values/themes.xml` and **`values-night/themes.xml`** — the source only has a Light parent, so the
window background flashes white on launch in dark mode. New launcher icons; do not reuse the
Smart Toolkit toolbox.

**`app/proguard-rules.pro`** — keep Room rules, add Tesseract rules, delete the AdMob/Billing/ML Kit
blocks.

**Verify:** `./gradlew assembleDebug`, app launches showing a placeholder.

---

## Phase 2 — Replace Hilt with manual DI

The graph is five nodes. Hilt costs a Gradle plugin, Dagger, and a whole annotation-processing round
to build it. KSP stays for Room only.

`TickboxApp.kt` + `AppContainer.kt`:

```kotlin
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

interface AppContainer {
    val database: NoteDatabase
    val noteRepository: NoteRepository
    val preferences: UserPreferencesRepository
    val imageStore: NoteImageStore
    val backupManager: NoteBackupManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val database by lazy { NoteDatabase.create(context) }
    override val noteRepository by lazy {
        NoteRepository(database, database.noteDao(), database.checklistItemDao(), database.noteImageDao())
    }
    override val preferences by lazy { UserPreferencesRepository(context.dataStore) }
    override val imageStore by lazy { NoteImageStore(context) }
    override val backupManager by lazy { NoteBackupManager(context, noteRepository, database, imageStore) }
}

class TickboxApp : Application() {
    lateinit var container: AppContainer
        private set
    override fun onCreate() { super.onCreate(); container = DefaultAppContainer(this) }
}
```

`by lazy` gives exactly Hilt's `@Singleton` semantics and is thread-safe. The per-DAO provider
methods disappear entirely — Room's generated class already exposes them.

ViewModel construction, one shared accessor plus a `Factory` per VM:

```kotlin
val CreationExtras.container: AppContainer
    get() = (this[APPLICATION_KEY] as TickboxApp).container

companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            NoteEditViewModel(
                savedStateHandle = createSavedStateHandle(),
                repository = container.noteRepository,
                preferences = container.preferences,
                imageStore = container.imageStore,
            )
        }
    }
}
```

Call sites go from `hiltViewModel()` to `viewModel(factory = NoteEditViewModel.Factory)`. This works
because `viewModel()` inside a `composable {}` is scoped to the `NavBackStackEntry`, whose
`SavedStateHandle` is seeded with route arguments — the same mechanism `hiltViewModel()` used, so
`savedStateHandle.get<String>("noteId")` keeps working. `lifecycle-viewmodel-compose` is already a
dependency, so no new libraries.

**Do not use `AndroidViewModel`, and do not add a CompositionLocal for the container.** Instead push
the two real uses of `Context` out of the ViewModels: image file I/O into a new `NoteImageStore`,
and export/import `Uri` streams into `NoteBackupManager`. Both ViewModels become plain-constructor
and context-free — the single biggest testability win of the extraction. Nothing in the composable
tree needs the container directly, so a CompositionLocal would just be a second injection path.

One wrinkle: `this[APPLICATION_KEY] as TickboxApp` throws in `@Preview`. Fix by splitting each
screen into a stateful `NoteListScreen(viewModel)` and a stateless `NoteListContent(state,
callbacks)` during migration — which is also what makes the Compose tests cheap.

---

## Phase 3 — Data layer

Destination root: `app/src/main/java/com/theamericanmaker/tickbox/`.

| Source | Destination | Changes |
|---|---|---|
| `data/model/Note.kt` | `data/model/Note.kt` | package; promote `iconStyle: String` → `ChecklistIconStyle` enum |
| `data/db/{NoteEntity,NoteDao}.kt` | `data/` | package only |
| `data/db/{ChecklistItemEntity,ChecklistItemDao}.kt` | `data/` | drop unused `setChecked` |
| `data/db/{NoteImageEntity,NoteImageDao}.kt` | `data/` | drop unused `insertAll`, `deleteAllForNote`; **add** `getFilePathsForNote(noteId)` |
| `data/db/AppDatabase.kt` | `data/NoteDatabase.kt` | **rewrite** — see below |
| `data/repository/NoteRepository.kt` | `data/NoteRepository.kt` | drop Hilt + 4 dead methods; rewrite checklist save; `deleteNote` returns orphaned paths |
| `data/preferences/UserPreferencesRepository.kt` | `data/UserPreferencesRepository.kt` | **185 → ~35 lines** |
| — | `data/NoteImageStore.kt` | **new** — lifted from `NotepadViewModel.saveImageToInternal`/`getImageFile` |
| `data/db/{HistoryEntry,HistoryDao}.kt` | — | **not migrated** |

**`NoteDatabase.kt`** — collapse to a fresh v1. Drop `HistoryEntry`/`HistoryDao` (calculator and
random-generator history). **Delete all four hand-written migrations**: `MIGRATION_1_2`, `2_3` and
`4_5` only exist to reach the current note schema, and `3_4` created the history table this app
doesn't have. No v1–v4 database exists in the wild under the new applicationId, so none can ever run.

```kotlin
@Database(
    entities = [NoteEntity::class, ChecklistItemEntity::class, NoteImageEntity::class],
    version = 1,
    exportSchema = true
)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun noteImageDao(): NoteImageDao
    companion object {
        fun create(context: Context) =
            Room.databaseBuilder(context, NoteDatabase::class.java, "tickbox.db").build()
    }
}
```

Set `exportSchema = true` and commit `app/schemas/1.json` from day one — Smart-Toolkit has
`exportSchema = false` and therefore no migration safety net. Starting clean is nearly free.

**`UserPreferencesRepository`** — keep only `ocrHintShown` and `dictationDisclosureAcknowledged`;
add `themeMode` and `dynamicColor`. Delete the other ~18 keys and every tally/tip/timer/ruler/
stopwatch/sound-meter/favorites/last-route accessor.

**Verify:** `./gradlew testDebugUnitTest` with `NoteDaoTest` + `NoteRepositoryTest` written here.

---

## Phase 4 — Backup layer and data continuity

This is the phase that de-risks the whole project: after it, the user's real data is provably
readable and everything downstream is UI work.

Migrate `NoteImportArchive.kt` (311 lines) **near-verbatim, package rename only**. It is the
security boundary and the best-written code in the feature — it rejects `..`, absolute paths,
backslashes, directories and non-`images/` entries, enforces the size/count caps, stages to cache,
imports inside a transaction, and rolls back created files on failure. Do not refactor it during the
move. Migrate `NoteExportImportManager.kt` → `data/backup/NoteBackupManager.kt`.

### The frozen archive contract

- ZIP with **no directory entries** (the importer throws on `entry.isDirectory`); exactly one
  `notes.json` at root; images flat at `images/<filename>`, filename matching `[A-Za-z0-9._-]{1,128}`.
- `notes.json`: `{"version": Int, "exportedAt": Long, "notes": [...]}`.
- Each note: `title`, `content`, `type` ∈ {`TEXT`,`CHECKLIST`}, `category`|null, `isPinned`,
  `iconStyle`, `createdAt`, `updatedAt`; optional `checklistItems[{text,isChecked,position,indentLevel}]`;
  optional `images[{filename,position}]`.

Every scalar is read with `optString`/`optBoolean`/`optInt`/`optLong` plus a default, so the
importer is **already tolerant of additive fields** in both directions. That property is what makes
this safe.

### Required changes

1. **Export `colorLabel`** — `buildExportJson` (`NoteExportImportManager.kt:80-90`) writes
   title/content/type/category/isPinned/iconStyle/timestamps but not `colorLabel`, even though
   `importNote` reads it. Add `noteJson.put("colorLabel", note.colorLabel ?: JSONObject.NULL)` after
   `category`. Without this the bug follows the code into Tickbox.
2. **Import `colorLabel` tolerantly** — mirror the existing `category` handling exactly:
   ```kotlin
   colorLabel = if (noteJson.isNull("colorLabel")) null
                else noteJson.optString("colorLabel").takeIf { it.isNotBlank() },
   ```
   `isNull()` returns `true` for an absent key as well as an explicit null, so a Smart Toolkit
   archive (which omits it) yields `null` — correct, since that data genuinely was lost.
3. **Version guard** — write `version = 2`; on read, `if (root.optInt("version", 1) > CURRENT) throw
   ImportValidationException("This backup was made by a newer version of Tickbox.")` — a clean error
   instead of future silent data loss.
4. **Change nothing else.** No renaming `filename`, no kotlinx-serialization, no ZIP restructuring.

### Tests written in this phase

- `NoteImportArchiveTest.kt` carried over (265 lines, all validation paths), plus cases for
  `colorLabel` present / absent / explicitly null.
- **`LegacyImportTest`** against the committed real-export fixture from Phase 0 — asserts note
  count, checklist text/order/`isChecked`/`indentLevel`, `isPinned`, `iconStyle`, timestamps, image
  association and position, and `colorLabel == null` (documenting the known loss). **The single most
  important test in the repo.**
- `RoundTripTest` — seed → export → wipe → import → deep equality *including* `colorLabel`.

### Migration runbook for the user

1. Smart Toolkit → notes → ⋮ → Export. Save to cloud/PC, then a second copy elsewhere.
2. Install Tickbox. Import → pick the ZIP.
3. Spot-check: note count, longest checklist, every image-bearing note, pinned notes at top.
4. Re-apply colour labels by hand — old exports never contained them (10 colours, per note).
5. **Keep Smart Toolkit installed at least two weeks.** Different applicationIds, so both coexist
   with separate databases and separate `filesDir`. Uninstalling is irreversible.

One caveat to state in the release notes: `importNote` always inserts, never upserts, so importing
the same ZIP twice duplicates everything. Accept this for 1.0 and warn in the import dialog — adding
a dedup key invites building sync, which is out of scope.

---

## Phase 5 — Feature code

Straight package rename from `feature/notepad/` into `ui/list/`, `ui/edit/`, `ui/share/`.

`NoteListScreen.kt` (462), `NoteEditScreen.kt` (782), `NotepadViewModel.kt` → `NoteEditViewModel.kt`
(433), `NoteListViewModel.kt` (128), `NoteShareFormatter.kt` (74), `components/{ChecklistItemRow,
ImageAttachmentRow, FullScreenImageViewer}.kt`, `smart/{NoteCategorizer,
ChecklistSuggestionProvider}.kt`, `templates/{TemplateProvider, TemplatePickerBottomSheet}.kt`.

Per-file edits: `hiltViewModel()` → `viewModel(factory = …)`; split each screen into stateful +
stateless halves; `UtilityTopBar` (29 lines, `ui/components/TopBar.kt`) inlined as `NotesTopBar`
with an optional `navigationIcon` slot so the list screen can use it too.

**Theme** — carry `ui/theme/{Theme,Color,Type}.kt`, rename `SmartToolkitTheme` → `TickboxTheme`, and
collapse the 5-way `AppColorTheme` enum (~80 lines of `Color.kt`) down to one brand seed plus
`dynamicLight/DarkColorScheme` on API 31+. The multi-preset picker was a toolkit settings feature
that isn't coming across.

**Navigation** — delete `Screen.kt`, `NavGraph.kt`, `NavigationStateViewModel.kt` (last-active-route
persistence is toolkit-wide behaviour). Replace with ~35 lines:

```kotlin
NavHost(navController, startDestination = "notes") {
    composable("notes") { NoteListScreen(...) }
    composable(
        route = "notes/{noteId}?type={type}",
        arguments = listOf(
            navArgument("noteId") { type = NavType.StringType },
            navArgument("type") { type = NavType.StringType; defaultValue = "TEXT" },
        ),
    ) { NoteEditScreen(onBack = { navController.popBackStack() }) }
}
```

`noteId` stays `StringType` — a `LongType` arg can't carry the `-1` "new note" sentinel.

**`MainActivity`** — drop `@AndroidEntryPoint`, the injected repository, `pendingNavigationRoute`,
`handleNavigationIntent`, `onNewIntent`, `SettingsViewModel`. Keep `enableEdgeToEdge()`.

**Not ported:** `ui/home/`, `ui/settings/`, `ui/guide/`, `data/model/UtilityItem.kt`, the 20 other
`feature/` packages, `HistoryEntry`/`HistoryDao`. Mine `ui/guide/UserGuideData.kt:245-269` for
README copy — it is already well-written user-facing prose — but do not ship an in-app guide.

**Keep `NoteCategorizer` and `ChecklistSuggestionProvider` for 1.0.** Both are arguably dead weight
(hard-coded English keyword maps; `category` is auto-derived and not user-editable), and deleting
them is a defensible 1.1 call once real tags exist. But extraction should be behaviour-preserving —
the category suffix is visible in the list today, and removing features is a separate decision from
moving them.

**Verify:** full manual pass — create/edit both note types, pin, colour, search, filter, images,
camera, dictation, share, templates, undo-delete, export, import. **Import the real export into the
debug build and start using Tickbox daily from here.**

---

## Phase 6 — Swap ML Kit OCR for Tesseract

The seam is tiny: `smart/ImageTextExtractor.kt` is a 41-line `object` exposing
`suspend fun extractText(File): String` and `fun splitIntoItems(String): List<String>`. Keep the
shape so `FullScreenImageViewer` and the editor flow are untouched — but change the return to
`Result<String>` so failures can surface.

1. Add `cz.adaptech.tesseract4android:tesseract4android:4.7.0` (Apache-2.0, an F-Droid-buildable
   wrapper over Tesseract + Leptonica). *Verify the current version on Maven Central; an `-openmp`
   variant also exists.*
2. Ship `eng.traineddata` in `app/src/main/assets/tessdata/`. Start with **tessdata_fast** (~2 MB);
   fall back to standard `tessdata` (~15 MB) if accuracy is unacceptable. Bundling beats
   downloading: no `INTERNET` permission, works offline, and F-Droid disallows fetching binaries at
   runtime.
3. On first use, copy the asset to `filesDir/tessdata/eng.traineddata` and call
   `TessBaseAPI.init(filesDir.absolutePath, "eng")` — Tesseract wants the **parent** of `tessdata`.
   Cache the instance; `recycle()` when done.
4. Run on `Dispatchers.Default` with a progress indicator. Tesseract takes ~1–3 s versus ML Kit's
   ~200 ms, and the current fire-and-wait UX will read as a hang without one.
5. **Image quality:** attachments are downscaled to 1920 px / JPEG-85
   (`NotepadViewModel.kt:245-277`). Raise the cap to 2560 px, and grayscale + contrast-boost
   immediately before OCR (not on the stored file).
6. Delete `mlkit-text-recognition` from the catalog and its ProGuard rules.

**Timeboxed spike, then decide:** run ten representative real photos. If quality is unacceptable,
drop OCR from 1.0 (delete the extractor, the viewer button, and the `ocr_hint_shown` pref) and
revisit later. Do not reintroduce ML Kit.

**Verify nothing else is proprietary.** Everything else kept is Apache-2.0 AndroidX or JetBrains and
pulls no Play Services. Confirm with:

```bash
./gradlew :app:dependencies | grep -E 'com.google.android.gms|com.google.mlkit'   # must be empty
```

One footnote: the test-only `org.json:json:20240303` carries the JSON.org "Good, not Evil" clause,
which Debian/Fedora/FSF classify as non-free. It never enters the APK, but Phase 8 drops it anyway —
Robolectric supplies AOSP's Apache-2.0 `org.json`, which also means the parser tests exercise the
same implementation production uses.

---

## Phase 7 — Fix the bugs

Each is an independent commit. Ordering matters where noted.

**B1 — `colorLabel` lost on export.** Done in Phase 4.

**B2 — Fake drag handle.** `ChecklistItemRow.kt:82-89` renders `Icons.Filled.DragHandle` with
`contentDescription = "Reorder"` and **no gesture modifier**; `onReorderChecklistItems(from, to)`
(`NotepadViewModel.kt:206-214`) is never called. This is also a TalkBack lie — "Reorder" announced
on a non-interactive element. **Wire it up in 1.0**: the app is named Tickbox, the reorder function
already exists, and a checklist you can't reorder is a broken checklist. Use
`sh.calvin.reorderable` (Apache-2.0, ~50 KB, F-Droid-safe) rather than hand-rolling. Requires B3's
stable IDs. *Fallback if it drags on: delete the icon — a missing feature beats a broken one.*

**B3 — Destructive checklist rewrite.** `NoteRepository.kt:62-74` does `deleteAllForNote` +
`insertAll` on **every autosave** (every 2 s while typing). So `ChecklistItem.id` is unstable, any
future per-item feature is impossible, and rowids grow unboundedly. Replace with a reconciling save
inside a transaction: update rows whose ID still exists, insert new ones, delete the difference.
This *un-deletes* `ChecklistItemDao.update`/`deleteById`, so **do B3 before the dead-code sweep**.
It also lets `LazyColumn` key on the real ID, which fixes recomposition and focus jank on insert and
delete for free.

**B4 — Orphaned image files.** Deleting a note cascades `note_images` rows but **leaves the JPEGs in
`filesDir/note_images/` forever** — nothing ever GCs that directory. Make `deleteNote` return the
file paths (via the new `getFilePathsForNote`), and have `NoteListViewModel` call
`imageStore.delete(paths)` once the 5 s undo window elapses. Add `NoteImageStore.deleteOrphans()` —
diff DB paths against directory contents — and run it once at startup off the main thread, to
reclaim what months of use have already leaked.

**B5 — HTML injection in share.** `NoteShareFormatter.formatAsHtml` interpolates title, content and
item text straight into `<h3>`/`<p>`/`<li>` with no escaping, so a note titled `A & B <3` produces
broken HTML in Gmail. Add an `esc()` for `&`, `<`, `>` and apply it everywhere. Also change the
signature to take domain `ChecklistItem` rather than `ChecklistItemUiState` so it is testable
without UI types.

**B6 — Dead `ChecklistIconStyle` enum.** Icon style is passed around as a raw `String`. Adopt the
enum and convert at the repository mapper (simpler than a Room `@TypeConverter`, and the DB column
and export format stay identical since it still writes `.name`).

**B7 — Dead code sweep** (after B3): delete `ChecklistItemDao.setChecked`,
`NoteImageDao.{insertAll, deleteAllForNote}`, `NoteRepository.{getChecklistItemsFlow, getImagesFlow,
getImagesForNote, deleteImage}`, `NoteDao.delete(note)`.

**B8 — `RECORD_AUDIO` in the manifest.** Remove. Dictation uses
`RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, which runs in the system recognizer's process — that
process holds the mic permission, not the caller. Verify on a device afterward.

**B9 — Silent OCR failure.** `FullScreenImageViewer.kt:127` swallows the exception with a
`// OCR failed silently` comment. Show a snackbar — three lines, removes a "the button does nothing"
bug report.

**B10 — Cosmetic.** `NoteListViewModel` builds `combine(notesFlow, MutableStateFlow(pending))` to
inject an already-known value; make it a `map`. And `showOcrHint` is assigned
`preferencesRepository.ocrHintShown` — the name inverts the meaning; rename and let the screen
negate. Both are the first things a contributor reads.

---

## Phase 8 — Tests and tooling

Principle: everything runs on the JVM in CI without an emulator, or it rots. Robolectric buys that
for Room and Compose alike.

**Add:** `kotlinx-coroutines-test`, `app.cash.turbine:turbine`, `org.robolectric:robolectric`,
`androidx.test:core-ktx`, `androidx.test.ext:junit-ktx`, `androidx.room:room-testing`,
`androidx.compose.ui:ui-test-junit4`, `debugImplementation` `ui-test-manifest`.
**Drop** `org.json:json:20240303`.

- **Pure JVM:** `NoteShareFormatterTest` (both types, checked/unchecked, indent, auto-numbering,
  blank filtering, the five HTML entities from B5); `ChecklistConversionTest` — extract the
  TEXT↔CHECKLIST logic out of `onToggleType` into a pure function first; `splitIntoItems`.
- **Robolectric + in-memory Room:** `NoteDaoTest` (pin ordering, type filter, LIKE search with `%`
  and `_` in the query, FK cascade); `NoteRepositoryTest` — including **checklist ID stability
  across repeated saves**, the regression test that would have caught B3, and `deleteNote` returning
  orphaned paths (B4).
- **Backup:** `LegacyImportTest`, `RoundTripTest`, `NoteImportArchiveTest` (Phase 4).
- **ViewModels** with `StandardTestDispatcher` + Turbine: autosave debounce (1999 ms → no save,
  2000 ms → one save), empty note not persisted, `savedNoteId` writeback, indent clamping, the
  5-image cap, and the undo window (delete → 4 s → undo survives; → 6 s → gone; delete A then B
  within the window → A commits immediately). Use `advanceTimeBy`, never `Thread.sleep`.
- **Compose, exactly five,** against the *stateless* content composables with hand-built state:
  list renders with pinned first; empty state; filter chip callback; checkbox toggle callback;
  checklist indentation and numbering. Resist growing this set.
- Commit `app/schemas/1.json` and add `MigrationTestHelper` scaffolding so the first real migration
  is a 20-line test rather than archaeology.

Target ~90 tests, whole suite under 60 seconds.

**Linting: ktlint** via `org.jlleitschuh.gradle.ktlint` 12.1.2. Single-purpose, autofixes with
`ktlintFormat`, no config beyond `.editorconfig`, matches the `kotlin.code.style=official` already
set. Detekt generates baseline-arguing on a one-person project and can't autoformat; Spotless just
wraps the same ktlint engine. Run the initial `ktlintFormat` as its **own commit** so it doesn't
pollute the migration diff.

`.editorconfig` must include
`ktlint_function_naming_ignore_when_annotated_with = Composable` — without it ktlint flags every
`@Composable fun NoteListScreen()` as a naming violation.

**Verify:** `./gradlew ktlintCheck testDebugUnitTest lintDebug`.

---

## Phase 9 — Open-source readiness

**LICENSE** — verbatim GPL-3.0. Sole copyright is the user's, so relicensing is unencumbered.
Use **SPDX two-line headers** per file rather than the 12-line FSF boilerplate:

```kotlin
// SPDX-FileCopyrightText: 2026 <Your Name>
// SPDX-License-Identifier: GPL-3.0-or-later
```

Machine-readable, REUSE-compliant, accepted by F-Droid, and far less noise across ~30 files in a
codebase whose selling point is approachability. Use `GPL-3.0-or-later`, not `-only`.

One consequence worth knowing now: GPL-3.0 forbids iOS App Store distribution and makes Google Play
legally contested (Play's ToS grants Google sublicensing rights GPL-3 forbids granting). **GitHub
Releases and F-Droid — the chosen channels — are unaffected.** If Play ever becomes a goal that's a
relicensing conversation; better decided now than later.

- **README.md** — pitch, badges, three screenshots, features (adapted from `UserGuideData.kt`),
  download (F-Droid badge + Releases + the signature warning below), **privacy** (lead with it: zero
  network permissions, verifiable from the manifest), the migration runbook, build instructions
  (`./gradlew assembleDebug`, no keystore needed), a 15-line architecture note, contributing.
- **CONTRIBUTING.md** — JDK 17, `ktlintFormat` before committing, how to run tests, Conventional
  Commits, and a short **scope statement** pointing at the leave-out list so feature PRs can be
  declined by reference rather than by argument. No CLA, no DCO.
- **CODE_OF_CONDUCT.md** — Contributor Covenant 2.1.
- **SECURITY.md** — private reporting via GitHub Security Advisories (enable in settings), 90-day
  disclosure, and an explicit scope note: the ZIP importer is the one untrusted-input surface and is
  already hardened against zip-slip, zip-bombs, traversal and duplicate entries. Say so — it signals
  seriousness. Exactly one exported component: `MainActivity`.
- **PRIVACY_POLICY.md** — adapt the existing one. Delete every network/ads/analytics clause. Keep
  and expand: images in app-private storage; Android Auto Backup may include notes and images
  (`backup_rules.xml` includes `note_images/`); **speech recognition sends audio to whatever
  recognizer the device has configured, usually Google** — the in-app one-time disclosure already
  says this and the policy must match; Tesseract OCR runs fully on-device.
- **CHANGELOG.md** (Keep a Changelog), issue/PR templates, `dependabot.yml` (gradle + actions,
  weekly, grouped minor+patch to avoid a firehose).

---

## Phase 10 — CI/CD and release

**Signing must be absent-file-tolerant** — a contributor with no keystore must get an *unsigned* APK
rather than a build failure, and **F-Droid's build server has no keystore either**, so this is a
hard requirement, not a nicety:

```kotlin
val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signingValue(prop: String, env: String): String? =
    keystoreProps.getProperty(prop) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
val releaseStoreFile: File? =
    signingValue("storeFile", "KEYSTORE_FILE")?.let { rootProject.file(it) }?.takeIf { it.exists() }

signingConfigs {
    create("release") {
        if (releaseStoreFile != null) {
            storeFile = releaseStoreFile
            storePassword = signingValue("storePassword", "KEYSTORE_PASSWORD")
            keyAlias = signingValue("keyAlias", "KEY_ALIAS")
            keyPassword = signingValue("keyPassword", "KEY_PASSWORD")
        }
    }
}
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = if (releaseStoreFile != null) signingConfigs.getByName("release") else null
    }
}
```

Ship `keystore.properties.example` documenting the four keys.

**`ci.yml`** — on push/PR to `main` (Smart-Toolkit's is `workflow_dispatch`-only, which is why its
badge is misleading): setup-java 17 temurin → setup-gradle →
`ktlintCheck` → `testDebugUnitTest` → `lintDebug` → `assembleDebug`, uploading reports and the APK.
Add `concurrency: cancel-in-progress`.

**`release.yml`** — on tag `v*`: decode the keystore from `KEYSTORE_BASE64` into `$RUNNER_TEMP`,
**verify the tag matches `versionName`** (catches the most common release mistake), run tests, build
`assembleRelease`, emit `SHA256SUMS.txt`, publish via `softprops/action-gh-release@v2` with
**`draft: true`** so the exact artifact can be smoke-tested before publishing, then delete the
keystore. Secrets: `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.

Build **APK**, not AAB — AAB is a Play format and Play is not a target.

---

## Phase 11 — F-Droid

Add fastlane metadata **in the repo**, so F-Droid picks it up automatically on every release
(retrofitting later is annoying):

```
fastlane/metadata/android/en-US/
├── title.txt              # Tickbox            (≤50 chars)
├── short_description.txt  # ≤80 chars
├── full_description.txt   # ≤4000 chars
├── changelogs/1.txt       # keyed by versionCode
└── images/{icon.png (512×512), phoneScreenshots/1..5.png}
```

Then fork `gitlab.com/fdroid/fdroiddata`, add `metadata/com.theamericanmaker.tickbox.yml`:

```yaml
Categories: [Writing]
License: GPL-3.0-or-later
SourceCode: https://github.com/TheAmericanMaker/Tickbox
IssueTracker: https://github.com/TheAmericanMaker/Tickbox/issues
Changelog: https://github.com/TheAmericanMaker/Tickbox/blob/main/CHANGELOG.md
RepoType: git
Repo: https://github.com/TheAmericanMaker/Tickbox.git
Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: app
    gradle: [yes]
AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

Run `fdroid lint` and `fdroid build -v -l` locally (Docker image available) before submitting.
Expect 1–4 weeks and review comments. Once merged, `UpdateCheckMode: Tags` means future releases are
picked up from git tags with no further MRs.

**Document the signature-mismatch gotcha in the README:** F-Droid signs with its own key, so the
F-Droid build and the GitHub Releases build cannot upgrade each other — switching sources requires
an uninstall, which loses all notes. Tell users to pick one source and stay on it. Making the build
bit-for-bit reproducible so F-Droid ships *our* signature is a worthwhile follow-up, not a 1.0
blocker.

Only after F-Droid inclusion is confirmed should Smart Toolkit be uninstalled.

---

## Post-1.0 backlog (ranked)

Weighted toward checklists, since that is what the name promises.

**1.1 — correctness and reach.** Strings extracted to `strings.xml` (enables localisation and a
Weblate contributor on-ramp, and forces an audit of every `contentDescription`); accessibility pass
(`ChecklistItemRow` uses 24 dp icon buttons for indent/outdent, half the 48 dp minimum); empty and
no-results states (all three currently render a blank screen that reads as a crash); edge-to-edge
and predictive-back verification.

**1.2 — checklist depth.** Deeper indent levels (hard-capped at 1 today by `indentLevel < 1`);
per-item actions; "uncheck all" / "delete checked".

**1.3 — organisation.** Real user-editable tags (a tags table + join + chip input), which subsumes
both the auto-categorizer and any folder feature — delete `NoteCategorizer` and
`ChecklistSuggestionProvider` when this lands; sort options; trash with 30-day purge; FTS4 to
replace `LIKE` (fine to ~1,000 notes today — do it when someone files an issue with a real corpus).

**1.4 — platform.** `ACTION_SEND` + `ACTION_PROCESS_TEXT` share-into target (~30 lines; the single
feature that turns an app you open into one you use); a "last exported 47 days ago" reminder banner
(the honest way to protect data when there is no sync); home-screen widget via Glance; biometric app
lock — and label it a lock, never encryption, since it isn't.

**Leave out permanently.** Markdown/rich text — the largest scope multiplier in notes apps; it
fights the checklist model, needs a renderer, and breaks the share/export formats. Reminders and
notifications — that drags in `POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`,
`RECEIVE_BOOT_COMPLETED`, WorkManager, and a permanent reliability tax across OEM battery managers;
it's a to-do app feature. Sync, accounts, cloud, collaboration — instantly converts a
weekend-maintainable app into a service with uptime, auth and conflict resolution; the ZIP export
*is* the sync story. Nested folders (tags subsume them). At-rest encryption (that's SQLCipher, a
different project).

---

## Verification

Per phase, from the Tickbox repo root:

```bash
./gradlew assembleDebug                        # Phases 1, 2, 5 — compiles and runs
./gradlew testDebugUnitTest                    # Phases 3, 4, 7, 8 — logic
./gradlew ktlintCheck lintDebug                # Phase 8 — format and Android lint
./gradlew :app:dependencies | grep -E 'com.google.android.gms|com.google.mlkit'   # Phase 6 — empty
./gradlew assembleRelease                      # Phase 10 — signing (unsigned locally is correct)
```

End-to-end on a device or emulator (API 26+):

1. Install Tickbox debug (`.debug` suffix) alongside Smart Toolkit.
2. Smart Toolkit → Notepad → ⋮ → Export.
3. Tickbox → Import that ZIP. Confirm note count, checklist items **with indent levels**, pins, icon
   styles, timestamps and image attachments survive. Colour labels will be absent — expected, that
   data was never exported by the old build.
4. Create a checklist, indent an item, **drag to reorder**, check items, back out, reopen — persists.
5. Delete a note with images, wait past the undo window, confirm the JPEGs are gone from
   `filesDir/note_images/` (`adb shell run-as`).
6. Share a note titled `A & B <3` as HTML — entities render correctly.
7. Attach a photo of printed text, Extract text — Tesseract returns text with a progress indicator
   and no ANR.
8. Dictate into title and content — the one-time disclosure appears, and **no mic permission prompt**.
9. Export from Tickbox, wipe app data, re-import — full round-trip with colour labels intact.
10. Toggle system dark mode — theme follows, and there is no white flash on cold launch.
