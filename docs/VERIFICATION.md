# Verification checklist

What CI cannot tell us. Everything here needs a device or emulator (API 26+).

Boxes marked done record real device runs, with dates and findings inline. Everything unmarked is
genuinely unknown — record what you find; a failure here is expected and useful, not a surprise.
The JVM test suite (`./gradlew testDebugUnitTest`) covers the data layer, the backup format, and
the editor state machine; this document covers the rest.

Suggested order: **B** first if re-verifying after data-layer changes, then **J** and **K** (the
newest untested code), then the rest.

---

## A. Migration from Smart Toolkit — DROPPED

**Dropped by owner decision, 2026-08-15:** the app has one user, the notes are grocery lists, and
re-typing them costs less than the ceremony below. Nobody is expected to run a real Smart Toolkit
export through Tickbox before release.

What survives from this section:

- The **archive format compatibility is still maintained** — it is now pinned by
  `NoteImportArchiveTest` and `BackupRoundTripTest` rather than by a manual runbook. A Smart
  Toolkit export should still import; it just isn't release-gating.
- The importer's limits still stand (64 MB archive, 200 images, 5 per note, 10 MB per image),
  and remain adjustable in `data/backup/NoteImportArchive.kt` if ever needed.

---

## B. Checklist persistence — the riskiest new code

`NoteRepository.saveNote` was rewritten to reconcile rows instead of deleting and reinserting the
whole checklist on every autosave. `NoteEditViewModel` has to carry ids in and read generated ids
back for that to work. Neither half has run.

- [ ] Create a checklist, add several items, back out, reopen — all items present, in order.
- [ ] Reopen, edit **one** item's text, wait past the 2s autosave, back out, reopen — only that
      item changed.
- [x] Add an item in the middle, save, then edit a **different** item. The edit lands on the item
      you meant, not a neighbour. *Passed 2026-08-15: the new row took a fresh id and the item it
      displaced kept its own, so the later edit landed on the row intended rather than the one now
      occupying that position. This is the box that distinguishes mapping by identity from mapping
      by index; the two are indistinguishable in the UI until text appears on the wrong line.*
- [x] Delete an item, save, reopen — it stays deleted, and nothing else shifted. *Passed
      2026-08-15: the row was gone, no orphaned `checklist_items` remained, and every surviving
      item kept its id. First execution of the delete branch of the reconciliation.*
- [x] Indent an item, save, reopen — indentation persisted. *Passed 2026-08-15.*
- [x] Check an item; it moves into the "N checked items" section. Reopen — still checked, still
      there. *Passed 2026-08-15 — checked state persisted.*

      Measured while confirming it: **the checked item kept its stored `position`.** It moves
      between sections visually without its underlying index changing, which is the display-order
      / list-index divergence that makes drag-to-reorder a remodelling job rather than a wiring
      one. That is no longer inference from reading the code — it is observed.
- [ ] Uncheck it; it returns to the main list.
- [x] Type in **bursts** in one item — a few words, pause ~3 seconds, repeat 10–15 times. When you
      reopen, the text should be complete and the list unchanged. **If item identity is breaking,
      this is where it shows.**

      The pauses are the test. `scheduleAutoSave` debounces rather than throttles — every keystroke
      cancels the pending job — so typing *continuously* fires autosave once, at the end, and
      exercises nothing. Each pause past 2s buys one more save. Temporarily dropping
      `AUTOSAVE_DELAY_MS` to ~200ms in a throwaway build gets the same coverage faster.

      Do not judge this one by eye — read the ids off the device, before and after:

      ```bash
      adb exec-out run-as com.theamericanmaker.tickbox.debug cat databases/tickbox.db > /tmp/t.db
      ```

      Pull `tickbox.db-wal` and `-shm` alongside it or you will read a stale 4 KB file: the
      database runs in WAL mode and the rows live in the WAL. Then
      `SELECT id,noteId,position,text FROM checklist_items ORDER BY noteId,position`. Same ids
      before and after means the reconciliation held; ids that have advanced mean it degraded to
      delete-and-reinsert, which looks identical in the UI.

      **Ran 2026-08-15 on a moto g stylus (2025), Android 16:** ids unchanged across ~15 autosaves,
      neighbouring items untouched, positions stable, text complete, nothing in logcat.
- [x] Switch a checklist to a text note and back. Item text survives the round trip (checked state
      and indentation are expected to be dropped — that conversion is lossy by design).

      Check that the item text is **visible in the body immediately**, not merely stored. Those are
      different things and only one of them is observable: `state.content` and the database can
      both be correct while the editor shows the empty body it had as a checklist, because the
      content field keeps its own `TextFieldValue` and follows only deliberate external writes.

      **Found 2026-08-15:** converting to a text note displayed an empty note. The text was never
      actually lost — it was in state, it was saved, and converting back restored it — but it read
      as total loss, and typing into the apparently-empty note would have overwritten the real
      content and made it loss in fact. `onToggleType` now emits the converted text so the field
      follows it. **Inherited** — Smart Toolkit's `onToggleType` has the same omission.

      Item ids changing across the round trip is correct, not a regression: the conversion rebuilds
      items from text, so the reconciliation inserts new rows and deletes the old ones.

---

## C. Autosave and back behaviour

- [ ] Type a title only, press back, reopen the list — the note is saved.
- [ ] Open a **new** note, type nothing, press back — no empty note is created.
- [ ] Type, then press back **before** 2 seconds elapse — the note still saves (back forces a save).
- [ ] Repeat that on a **new** note ten times in a row, as fast as you can. Exactly one note should
      appear per attempt. Two is the failure.

      This is now a regression check rather than a hunt. `savedNoteId` is only assigned after the
      insert returns, so a back-press save overlapping the timer's save could once have had both
      read it as 0 and insert separately. `saveNow` is serialised behind a `Mutex`, which removes
      the window rather than narrowing it.

      **Ran 2026-08-15, 11 attempts before the fix: no duplicates.** Read that result carefully —
      pressing back comfortably inside 2s means the timer never fired, so only one save path ran
      and the race was never actually attempted. The window is a few milliseconds around the point
      where the timer fires and back is pressed together, which is not reliably hittable by hand.
      The fix was applied because the window was real in the code, not because it was observed.

      Judge duplicates by insert *timing*, not by content — a test that types the same word each
      round produces identical notes legitimately. Two rows created within a few milliseconds of
      each other is the signal; seconds apart is just you.

      **Inherited, not a port defect.** Smart Toolkit's `NotepadViewModel` has the same shape
      (plain `savedNoteId`, cancel-and-restart `autoSaveJob`, assignment after the insert), so it
      is reachable there too and did not on its own block 1.0.
- [ ] Predictive back / gesture back behaves the same as the top-bar arrow.
- [x] Open several notes, read them, back out of each **without typing**. None of their `updatedAt`
      values change and the list order does not move.

      **Found 2026-08-15:** back-press saved unconditionally, so merely opening a note rewrote it.
      `updatedAt` came to mean "last looked at" rather than "last changed", and since the list
      sorts on it, browsing the library permanently reordered it — the note you actually edited
      last sinks while the one you glanced at rises. `save()` now skips when nothing changed.
      **Inherited** — Smart Toolkit has no dirty tracking either.

      That guard is only safe while every user mutation routes through `scheduleAutoSave`, which
      is what marks the note dirty. **`onAddChecklistItem` did not**, and was being covered by the
      unconditional save; it does now. A new mutating function that forgets will have its change
      dropped on back rather than quietly caught, so re-run this audit when adding one:

      ```bash
      grep -n "fun on\|_uiState.update\|scheduleAutoSave()" app/src/main/java/com/theamericanmaker/tickbox/ui/edit/NoteEditViewModel.kt
      ```

      `extractTextFrom` will show up as missing it and is fine: the only state it touches directly
      is the progress flag, and its text reaches a save through `insertLines`.

---

## D. Images

- [x] Attach from gallery. Thumbnail appears. *Passed 2026-08-15, after the fix in `4888983` —
      this failed for every image before it.*
- [x] Take a photo. Camera permission is requested on first use; denying it fails gracefully.
      *Capture passed 2026-08-15 after `ebf32b9`. **The deny path is still untested** — only the
      granted path has been exercised.*
- [x] Attach 5 images, then try a 6th — it is refused rather than silently dropped. *Passed
      2026-08-15: the cap is enforced by **hiding** the Gallery and Camera tiles at 5, so a sixth
      cannot be attempted rather than being rejected with a message. The `MAX_IMAGES_PER_NOTE`
      guard behind it returns silently, so if that affordance ever becomes reachable at the cap,
      the drop would be silent. All three constants agree at 5 — editor, attachment row, and the
      import path.*
- [ ] Tap an image — full-screen viewer opens, pinch-zoom and pan work.
- [ ] The OCR badge shows on thumbnails and the viewer has an "Extract text" button — both
      appeared automatically when the Tesseract engine landed (they were gated on one existing).
      Full OCR checks live in section J.
- [x] Remove an image — it disappears, and the file is gone from disk:
      `adb shell run-as com.theamericanmaker.tickbox.debug ls files/note_images`
      *Passed 2026-08-15 — row and file both gone.*
- [x] Delete a note that has images. Wait past the 5s undo window. The files are gone from
      `note_images/`. **This is the orphan cleanup working.** *Passed 2026-08-15: the note row
      went, its `note_images` row cascaded, and the file was removed from disk. This is
      `deleteNote` returning paths for the caller to delete once the undo window closes — a
      different path from the startup sweep below, and worth keeping distinct when one of them
      fails.*
- [x] Then relaunch the app and confirm the startup sweep did **not** delete anything still in use.

      A fresh library cannot test this at all. Every file in it is minutes old, the sweep skips
      anything under 24h, and so it correctly does nothing — which is indistinguishable from a
      sweep that is broken. Waiting for an older library is not much better, because by then it is
      running against data you care about. Backdate probe files instead:

      ```bash
      P=com.theamericanmaker.tickbox.debug
      adb shell "run-as $P sh -c 'cd files/note_images && echo x > orphan-old.jpg && echo x > orphan-fresh.jpg && touch -t 202601010900 orphan-old.jpg && touch -t 202601010900 <a-real-referenced-image>.jpg'"
      adb shell am force-stop $P && adb shell am start -n $P/com.theamericanmaker.tickbox.MainActivity
      ```

      Three outcomes, one per branch: `orphan-old.jpg` **deleted**, `orphan-fresh.jpg` **kept**
      (the 24h guard), and the backdated real image **kept** — that last one proving the database
      decides what is referenced, not the file's age. Back the real images up first with
      `adb exec-out run-as $P cat files/note_images/<name>` in case the sweep gets it wrong, and
      remove the leftover probe afterwards.

      **Ran 2026-08-15, first ever execution: all three correct.** No rows left pointing at missing
      files, nothing in logcat.

---

## E. Sharing

- [ ] Share a text note. Both plain text and formatted HTML arrive intact in the receiving app.
- [ ] Share a checklist. Numbering, ☐/☑ boxes, indentation and strikethrough all render.
- [ ] **Create a note titled `A & B <3` and share it to an HTML-aware target (Gmail).** The title
      renders literally, with no broken markup. This was a real bug in the original.

---

## F. List screen

All of section F was verified 2026-08-15 over adb — `input swipe` / `input tap` to drive it and
`screencap` to read the result. Two notes on doing it that way, both learned the hard way:

- **Check which screen you are on before every gesture, with a screenshot.** A swipe that misses a
  card opens the note under it, and every subsequent "swipe" then types into the editor instead. It
  is silent, and it edits real notes. `dumpsys activity` will not tell you: one `MainActivity` hosts
  both screens through Compose navigation, so it reports the same either way.
- **Getting back out takes two BACKs when the keyboard is up.** The first is consumed dismissing the
  IME and the editor stays open — which looks exactly like having left, if you only check the
  activity name. Confirm with `mInputShown` from `dumpsys input_method`, or just screenshot again.
- **Deleting a card shifts everything below it.** Swiping the same coordinate twice hits a different
  note the second time, or none. Delete the *lower* card first and the upper one keeps its position.

For the empty states, swapping the database is far safer than deleting notes through the UI:
force-stop, `cat` a prepared database over `databases/tickbox.db`, delete the `-wal` and `-shm`, and
relaunch. Back up the images too — an empty database makes every one of them an orphan, and any file
older than the 24h guard is swept on that launch.

- [x] Empty library shows "No notes yet. Tap + to write your first one." *Passed.*
- [x] Search with no matches shows a **different** message naming the query. *Passed —
      "No notes match "…".", with the query quoted.*
- [x] Filtering to Checklists with none shows a **third** message. (All three used to be the same
      text, which read as the search having broken.) *Passed — "No checklists yet. Tap + to start
      one." All three messages and all three icons are distinct.*
- [x] Swipe a note left — it deletes with an Undo snackbar; Undo restores it. *Passed.*
- [x] Let the snackbar expire — the note is really gone. *Passed.*
- [x] Delete a note, and while the snackbar is still showing, delete a **second** one. The first
      commits immediately; only the second is undoable. *Passed — the first was committed and only
      the second came back.*
- [x] Pin and unpin; pinned notes sort to the top under a "Pinned" header. *Passed — "Pinned" and
      "Other" headers both render and the pin icon fills.*

      **Pinning gives no visible feedback.** The note moves to the top of the list, but the list
      does not scroll to follow it, so from anywhere below the fold the app appears to do nothing.
      Not a correctness bug — the sort, headers and icon are all right — but it reads as a dead
      button. Scrolling the pinned item into view would fix it; left alone deliberately, since it
      is a design decision rather than a defect.
- [x] Colour labels tint the note cards, and the card stays opaque while swiping (no red bleed
      through from the delete background).

      **Found and fixed 2026-08-15:** every card sat inside a permanent red frame with no swipe in
      progress. `SwipeToDismissBox` draws its background across the whole row and the card applied
      its own inset, so the margin exposed the `errorContainer` fill. The inset moved to the swipe
      container. **Inherited** — Smart Toolkit builds the row the same way.

      The **tint** half of this box is still unverified: no note in the test library has a colour
      label set.

---

## G. Dictation

- [ ] First use shows the voice-input disclosure dialog. Cancel dismisses it without recording.
- [ ] Accept it — dictation runs, and the dialog does not reappear next time.
- [ ] **No microphone permission prompt appears at any point.** `RECORD_AUDIO` was removed
      deliberately: `RecognizerIntent` runs in the system recogniser's process, which holds the
      permission. If a prompt or a crash appears here, that removal was wrong.
- [ ] Dictate into a text note with the caret mid-text — the text inserts at the caret, not the end.
- [ ] Dictate into a checklist — it splits into separate items.
- [ ] On a device with no recogniser, it shows a message rather than crashing.

---

## H. Theme and system integration

- [ ] Cold launch in dark mode — **no white flash** before the UI paints (this is what
      `values-night/themes.xml` is for).
- [x] Toggle system dark mode with the app open — the theme follows. *Passed 2026-08-15, driven
      with `adb shell cmd uimode night no|yes` while the app was foregrounded. Both variants are
      coherent; no stranded colours.*
- [x] On Android 12+, colours follow the system wallpaper (Material You). *Passed — the light and
      dark variants are the same wallpaper-derived family, not the stock purple baseline.*
- [x] Rotate the device mid-edit — text, caret and checklist state survive. *Passed 2026-08-15 in
      the checklist editor: title, all four images, colour and style pickers and every item came
      through, and the item ids were unchanged afterwards, so the rotation did not trigger a
      churning re-save. Landscape also reveals both Gallery and Camera tiles, which are clipped in
      portrait.*

      Drive it with `settings put system accelerometer_rotation 0` then
      `settings put system user_rotation 1`, and put both back afterwards. **Caret position is
      still unverified** — no field was focused for this run.
- [ ] The launcher icon renders correctly, including themed/monochrome mode on Android 13+.
      It is currently **placeholder art** and needs replacing before release.

---

## I. Release build

- [ ] `./gradlew assembleRelease` succeeds. It is minified with `isShrinkResources = true` and
      currently **unsigned** — signing arrives in Phase 10.
- [x] **No device needed for this one.** Check that R8 left the persisted enum names alone — every
      one of these should print `ok`:

      ```bash
      for s in TEXT CHECKLIST CHECKBOX CIRCLE STAR HEART SQUARE SYSTEM LIGHT DARK; do printf "%-10s %s\n" "$s" "$(unzip -p app/build/outputs/apk/release/app-release-unsigned.apk classes.dex | strings -a | grep -qF "$s" && echo ok || echo MISSING)"; done
      ```

      **Do not use `mapping.txt` for this.** It will show `NoteType -> g4.e`, which looks like a
      failure and is not — R8 renames the *class* while leaving the constant name strings alone,
      and the strings are the only part that matters.

      Why it matters: `NoteType` and `ChecklistIconStyle` are written to SQLite as `enum.name`, to
      the backup JSON by `buildExportJson`, and `ThemeMode` to DataStore — and every read is a
      tolerant `fromName(…) ?: TEXT`. A rename would not crash. Imports from Smart Toolkit would
      arrive as empty text notes, exports would stop being readable by Smart Toolkit, and a mapping
      that shifted between two releases would reset an existing library on upgrade. All silent, and
      none of it visible in a debug build. `proguard-android-optimize.txt` keeps `values()` and
      `valueOf()` but **not** the constants themselves, so nothing in the config guarantees this.

      **Ran 2026-08-14 against AGP 8.13.2 / Kotlin 2.1.0 / R8: all ten present, passes.** Recheck
      after any AGP or R8 bump, since nothing pins the behaviour. If it ever fails, the fix is
      `proguard-rules.pro`:

      ```
      -keepclassmembers enum com.theamericanmaker.tickbox.data.model.** { *; }
      -keepclassmembers enum com.theamericanmaker.tickbox.data.UserPreferencesRepository$ThemeMode { *; }
      ```

      A failure here would be **inherited**, not a port defect: Smart Toolkit ships minified with
      the same defaults and the same missing rules.
- [ ] Install that APK and repeat at least sections B, J and K. **R8 has never been exercised
      against this code end-to-end**, and Room plus reflection is exactly where shrinking tends to
      break. If something works in debug and not in release, suspect `proguard-rules.pro`. The
      release build now also carries Tesseract's JNI surface — run one OCR extraction on the
      minified build specifically (keep rules exist for `com.googlecode.tesseract.android.**` and
      leptonica, but only a run proves them).

---

## J. OCR — Tesseract quality spike

Wired 2026-08-15 (tesseract4android 4.9.0 + bundled `tessdata_fast/eng`). Compiles and packages;
**never executed**. This is a quality decision, not just a works/doesn't check: the plan's fallback
is to *drop OCR from 1.0* if results embarrass the app, and the upgrade path is the standard
`tessdata` model (~15 MB instead of ~4 MB).

- [ ] Attach a photo of a **flat printed page** (book, letter). Extract. Expect near-perfect text.
- [ ] A **shopping receipt** (narrow columns, small type).
- [ ] A **product label** (curved surface).
- [ ] A **handwritten list** — expect this to be poor; Tesseract does not do handwriting. Confirm
      it fails politely (garbage text or "No text found", never a crash).
- [ ] **Ten photos of the kind you actually take**, since grocery/errand snapshots are the real
      workload. Judge: would you trust the output enough to keep the feature?
- [ ] First extraction on a fresh install includes the one-time model copy from assets — confirm
      it completes and that a **second** extraction is faster.
- [ ] The button shows "Extracting text…" with a spinner for the seconds Tesseract needs, and the
      UI stays responsive throughout (recognition runs off the main thread; a frozen UI here is a
      bug, not a slow engine).
- [ ] Extraction failure (try a 0-byte or corrupt image if you can craft one) surfaces a snackbar,
      not silence.
- [ ] Extracted text lands as checklist items in a checklist, and as appended text in a text note.
- [ ] The "Tip: tap an image to extract text" hint appears once, on the first image ever attached,
      and never again.
- [ ] Airplane mode changes nothing — the engine is fully on-device.

**Verdict box:** keep `tessdata_fast` / upgrade to standard `tessdata` / drop OCR from 1.0.

---

## K. Drag-to-reorder — on device

Implemented 2026-08-15, key-based. The state-machine move is unit-tested; the gesture is not.

- [ ] Long-press the handle and drag an item one position. It lands exactly where dropped, and
      **stays** after backing out and reopening (positions are rewritten on save).
- [ ] Drag the top unchecked item to the bottom of the unchecked section, and bottom to top.
- [ ] With **checked items present**, drag an unchecked item downward past the "Add item" row —
      it must not enter the checked section, and nothing should crash or teleport.
- [ ] Drag with a **blank item** in the middle of the list. The blank row moves like any other.
- [ ] Drag an **indented** item — indentation travels with it.
- [ ] Reorder, then press back within 2 seconds. The new order persists (the mid-save id
      write-back guard is exactly this scenario).
- [ ] Reorder while the numbered labels are visible — numbering re-flows to match the new order.
- [ ] The row visibly lifts (highlight) while dragged, and drops cleanly.
- [ ] TalkBack: the handle announces "Reorder" and is now a real, functioning control. (Gesture
      alternatives for accessibility are a known gap — note what TalkBack offers, if anything.)
- [ ] Checked items have **no** drag handle.

---

## Reporting back

For anything that fails, the useful details are: which box, what you expected, what happened, and
whether it reproduces in debug, release, or both. Logcat filtered to the app helps. Note whether it
is a **port defect** (Smart Toolkit did this correctly) or **inherited** (the original did it too) —
that distinction decides whether it blocks 1.0.
