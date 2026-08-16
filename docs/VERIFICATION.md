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
- [x] Type **continuously** for 30 seconds in one item, then stop. Nothing is lost, and ids hold.

      **Ran 2026-08-15 on a Galaxy Z Fold 5:** 399 characters typed over 30 s. All 405 characters
      persisted once typing stopped, and ids were unchanged (`9, 10, 11`) — the reconciliation holds
      under sustained editing.

      Found and fixed in the same pass — [#17](https://github.com/TheAmericanMaker/Tickbox/issues/17):
      **nothing at all reached the database during those 30 seconds.** `scheduleAutoSave` cancelled
      the pending job on every keystroke, so a fast typist never reached the 2 s delay and the
      unsaved window was unbounded. **Inherited** — Smart Toolkit cancels and relaunches the same
      way.

      The wait is now also capped at 10 s from the first unsaved edit. **Verified:** after 14 s of
      continuous typing, reading the database immediately — before the 2 s debounce could have
      fired — showed the item grown from 179 to 313 characters. Before the fix that read returned
      the original 179. Reading "immediately" is the whole test; leave it longer than 2 s and the
      debounce fires and tells you nothing.
- [x] Add items by pressing **Enter** repeatedly until the list is longer than the visible area.
      Each new row should take the caret.

      **Found and fixed 2026-08-15 —
      [#16](https://github.com/TheAmericanMaker/Tickbox/issues/16).** Enter created the row
      correctly (the database showed 7
      rows while the screen showed 3), but if the new row is below the fold it is never composed,
      `requestFocus()` fails, and `NoteEditScreen`'s collector swallows it:

      ```kotlin
      // The row has to exist and be composed before it can take focus.
      delay(100)
      if (index in focusRequesters.indices) {
          runCatching { focusRequesters[index].requestFocus() }
      }
      ```

      The comment names the precondition; `delay(100)` cannot satisfy it, because nothing scrolls
      the new row into view. The caret silently stays put and every further keystroke appends to the
      **previous** item — `Charlie` became `CharlieDeltaEcho`. Confirmed both ways: adding a row at
      the top of the list, where it is on screen, focuses correctly.

      It bit at item 4 on a Fold 5 cover display; on a normal phone expect roughly item 7. That is
      well inside the length of an ordinary shopping list, which is what the app is for.

      **Inherited** — Smart Toolkit's `NoteEditScreen.kt:167` is the same code, and its only
      `animateScrollToItem` is the scroll-to-top affordance.

      Fixed by scrolling the row into view before requesting focus. Unchecked rows are one lazy item
      each in list order, so the mapping is "count the unchecked items ahead of it"; the scroll only
      runs when the row is genuinely off screen, since `scrollToItem` parks it at the top of the
      viewport and that would lurch for a row already visible. **Verified:** the same
      Alpha/Bravo/Charlie/Delta/Echo sequence now produces five separate rows, on a note whose
      attached image makes the fold tighter still.
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
- [x] Attached photos appear **the right way up**, from both the camera and the gallery, whichever
      way the phone was held.

      **Failed then fixed 2026-08-15 —
      [#14](https://github.com/TheAmericanMaker/Tickbox/issues/14).** Every camera photo was stored
      rotated: shot normally it landed on its side, shot with the phone sideways it landed
      upside-down. `saveFromUri` decoded with `BitmapFactory` (which ignores EXIF `Orientation`) and
      re-encoded with `compress` (which writes none), so the rotation was baked into the pixels and
      the tag that would have corrected it destroyed. Confirmed on the pulled files: three camera
      photos all stored 2000×1500 landscape with **no EXIF**; the only image that read correctly was
      a screenshot, which never depended on a tag.

      **Inherited**, but it was not therefore non-blocking — Smart Toolkit used ML Kit, which takes
      a rotation hint and tolerated it. Tesseract does not, so this broke section J outright.

      Fixed by reading the tag before decoding and baking the rotation into the pixels. **Verified
      with a crafted fixture:** an 800×400 landscape JPEG tagged `Orientation=6` now stores as
      **400×800 portrait** with the marker band on the right edge — matching exactly what the system
      photo picker renders. All eight orientations are handled in code, mirrors included, but only
      `6` has been driven end-to-end through the device.

      **Confirmed again 2026-08-15 on real camera photos**, which is the stronger evidence: a photo
      taken with the phone upright now stores **1500×2000 portrait**. The sensor produces 2000×1500
      landscape natively, so a portrait file can only mean the rotation was applied — before the fix
      every camera photo stored 2000×1500 regardless of how it was taken. The owner confirms they
      display the right way up.

      To re-test: generate fixtures with PIL (`exif[274] = orientation`), `adb push` them to
      `/sdcard/Pictures/`, broadcast `MEDIA_SCANNER_SCAN_FILE` so the picker sees them, attach, then
      pull from `files/note_images` and compare dimensions. The stored size flipping from landscape
      to portrait is the assertion — it needs no eyes.

      **Images attached before this fix are not recoverable.** The rotation is baked in the wrong
      way and there is no tag left to correct from; they need re-attaching.
- [x] Take a photo **with the phone in a different orientation than the editor**, and confirm it
      attaches. *Failed 2026-08-15 —
      [#15](https://github.com/TheAmericanMaker/Tickbox/issues/15): the photo was silently
      discarded. `cameraImageUri` was plain `remember`, so the rotation recreated the activity and
      the pending uri was gone by the time the camera returned. No snackbar, and the temp file
      leaked too.*

      *Fixed with `rememberSaveable` for both, plus a message when the capture succeeds but its
      destination was lost.*

      **Verified 2026-08-15:** rotating the device with the camera open now attaches the photo. The
      image count went 2 → 3, and the new file carries the rotation correctly.

      The leaked temp files are fixed too, and the old ones are the proof. `cache/camera_temp` still
      held **three** orphans from the original failing session — and each is a full 4000×3000 JPEG
      carrying **`EXIF Orientation = 3`**, the 180° case. They are the photos that "vanished": the
      capture succeeded, the uri was lost across the recreation, and the cleanup handle went with
      it. Their orientation tag is also the independent confirmation of
      [#14](https://github.com/TheAmericanMaker/Tickbox/issues/14) — the camera really was writing a
      rotation the app then ignored. Captures after the fix leave nothing behind.

      Two things worth knowing:

      - **A failed capture is recoverable while it lasts.** The full-resolution original sits in
        `cache/camera_temp` until Android reclaims the cache:
        `adb exec-out run-as com.theamericanmaker.tickbox.debug cat cache/camera_temp/<name> > out.jpg`
      - **Nothing sweeps `camera_temp`.** Orphans only accumulate when a capture fails, and the
        system clears `cacheDir` under pressure, so this is a wart rather than a leak — but a
        startup sweep of files older than a day would cost little, mirroring what `note_images`
        already does.
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
- **Drive by accessibility node, not by pixel.** `adb shell uiautomator dump` gives exact bounds for
  every control, and Compose populates it well — the drag handle appears as `Reorder`, rows as
  `EditText`, the trash icons as `Delete item`. Coordinates read off a screenshot go stale the
  instant the keyboard opens or a row is added; a tap computed before the IME appeared landed in the
  title field and quietly concatenated five words into it. Re-dump before every gesture.
- **`uiautomator` only reports what is on screen.** With the keyboard up, a checklist showed three
  rows while the database held seven. Do not conclude "the row was not created" from the dump —
  check the database.
- **One BACK too many leaves the app entirely.** From the list screen, BACK pops Tickbox off its
  task and reveals whatever task was behind it, which after enabling USB debugging is Settings. If
  gestures suddenly appear to do nothing, check the foreground package before assuming a bug.

On a **foldable**, two more:

- `screencap` warns `Multiple displays were found` and picks one arbitrarily, so it may capture the
  screen you are not using — and the warning text prefixes the PNG bytes, corrupting a naive
  redirect. Pass `-d`, and get the ids from `dumpsys SurfaceFlinger --display-id`.
- The *logical* display id is what `input` needs, and it is not the same number. `dumpsys display`
  maps them: while folded, the cover screen is logical display 0, so `input` needs no `--display`.

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

**Polish pass 1 (`6a8f6c6`), verified 2026-08-15 on a Galaxy Z Fold 5:**

- [x] The **+ button opens a menu** ("New checklist" / "New note") instead of creating whichever
      type matched the active filter chip. *Passed — both entries present and each creates the
      type it names.*
- [x] Checklist cards show **progress**, not the word "Checklist". *Passed — a list of 5 with one
      item checked read "1 of 5 done" with a progress bar. This is backed by a grouped count query
      combined with the notes flow, so it exercises two live sources rather than one.*
- [x] Recent notes show **"Today" / "Yesterday"** rather than a calendar date. *Passed — the card
      read "Today". Note `formatNoteDate` uses `java.time`, which is fine at `minSdk 26` without
      desugaring.*
- [ ] "All N done" appears when a checklist is fully checked. *Not yet exercised — the test list
      always had unchecked items.*
- [ ] List rows animate placement on pin, delete and undo (`Modifier.animateItem`). *Not
      verifiable over adb; needs eyes.*

---

## G. Dictation

**Reported working 2026-08-15** by the owner on a Galaxy Z Fold 5, as a whole flow. The boxes below
record which parts that statement actually settles and which are still individually unexercised —
"dictation is fine" covers the happy path, not the edge cases it was written to catch.

- [ ] First use shows the voice-input disclosure dialog. Cancel dismisses it without recording.
      *The cancel branch specifically is unexercised.*
- [x] Accept it — dictation runs, and the dialog does not reappear next time. *Dictation ran.*
- [x] **No microphone permission prompt appears at any point.** `RECORD_AUDIO` was removed
      deliberately: `RecognizerIntent` runs in the system recogniser's process, which holds the
      permission. If a prompt or a crash appears here, that removal was wrong.

      *Confirmed 2026-08-15, and this one is provable rather than observed: `RECORD_AUDIO` appears
      neither in `AndroidManifest.xml` nor in the built APK, so Android cannot raise a runtime
      prompt for it at all. Dictation working regardless is what validates the removal.*

      ```bash
      aapt2 dump permissions app/build/outputs/apk/debug/app-debug.apk
      ```
- [ ] Dictate into a text note with the caret mid-text — the text inserts at the caret, not the end.
      *Not separately exercised; needs the caret deliberately placed mid-text.*
- [ ] Dictate into a checklist — it splits into separate items.
      *Not separately exercised.*
- [ ] On a device with no recogniser, it shows a message rather than crashing.
      *Hard to test on hardware that has one; needs an emulator image without it.*

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

- [x] `./gradlew assembleRelease` succeeds. It is minified with `isShrinkResources = true`, and
      signed only when a keystore is configured.
      *Ran 2026-08-15 locally: succeeds in 47 s. With no `keystore.properties` present it produced
      `app-release-unsigned.apk` rather than failing, which is the intended F-Droid/contributor
      fallback. **31 MB** release, **47 MB** debug — Tesseract contributes ~30 MB of native libs
      across four ABIs plus a 4 MB model, and R8 does not shrink native code. `x86`/`x86_64` are
      emulator-only for a phone app; an ABI split would roughly halve the download.*
- [x] **No device needed.** Check that R8 kept the Tesseract JNI surface. Native code looks these
      classes up by name, so a rename breaks OCR in release only:

      ```bash
      for c in com/googlecode/tesseract/android/TessBaseAPI com/googlecode/leptonica/android/Pix com/googlecode/leptonica/android/ReadFile; do printf "%-52s %s\n" "$c" "$(unzip -p app/build/outputs/apk/release/app-release-unsigned.apk classes.dex | grep -qa "L$c;" && echo PRESENT || echo MISSING)"; done
      ```

      *Ran 2026-08-15: all three present — the `-keep` rules in `proguard-rules.pro` hold. This is
      necessary but not sufficient; only an actual extraction on the minified build proves the JNI
      wiring, which is the last box in this section.*
- [x] **No device needed.** Confirm the privacy claim against the built artifact, not the source:

      ```bash
      aapt2 dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
      ```

      *Ran 2026-08-15: the merged release manifest declares only `android.permission.CAMERA` (plus
      the framework's own dynamic-receiver permission). **No `INTERNET`** — Tesseract and its
      transitive dependencies added none, so `PRIVACY_POLICY.md`'s headline claim survives the
      dependency. Worth re-running whenever a dependency is added, since a library can introduce a
      permission through manifest merge without any source change.*
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
- [x] Install that APK and repeat at least sections B, J and K. **R8 has never been exercised
      against this code end-to-end**, and Room plus reflection is exactly where shrinking tends to
      break. If something works in debug and not in release, suspect `proguard-rules.pro`. The
      release build now also carries Tesseract's JNI surface — run one OCR extraction on the
      minified build specifically (keep rules exist for `com.googlecode.tesseract.android.**` and
      leptonica, but only a run proves them).

      **Ran 2026-08-16 on a Galaxy Z Fold 5 — R8 breaks nothing.** Everything below was exercised
      in the minified build, not inferred from the debug one.

      | | |
      | --- | --- |
      | Cold launch | 265 ms, no crash |
      | Room, autosave, reconciliation | items typed, reordered, ticked, all persisted |
      | Drag-to-reorder, drag-to-indent | both work — the reorderable library survives minification |
      | Checklist ↔ note round trip | indent written as spaces, tick restored on the way back |
      | **Tesseract OCR** | extracted four lines; log shows `Tesseract(native): Initialized Tesseract API with language=eng`, no `UnsatisfiedLinkError` |
      | **Enum names across a restart** | `ThemeMode.DARK`, `NoteType.CHECKLIST` and `ChecklistIconStyle.STAR` all read back correctly |
      | **Backup JSON** | `"type": "CHECKLIST"`, `"iconStyle": "STAR"`, `"version": 2` — unmangled, every field present, image included |

      The last two are the ones that mattered: they are the failure modes this section warned
      would be *silent*, and they were previously checked only by grepping the DEX for strings.
      A string being present does not prove the round trip; these runs do.

      **No release keystore is needed to repeat this.** Sign the unsigned APK with the debug key:

      ```bash
      apksigner sign --ks ~/.android/debug.keystore --ks-pass pass:android --key-pass pass:android \
        --ks-key-alias androiddebugkey --out signed.apk app/build/outputs/apk/release/app-release-unsigned.apk
      ```

      It installs alongside the debug build under its own applicationId, so it starts with an empty
      library and cannot touch real notes. Note `run-as` does not work on it — a release build is
      not debuggable, so everything has to be verified through the UI rather than by reading the
      database. Uninstall it afterwards; it is a second icon in the launcher.

---

## J. OCR — Tesseract quality spike

Wired 2026-08-15 (tesseract4android 4.9.0 + bundled `tessdata_fast/eng`). Compiles and packages;
**never executed**. This is a quality decision, not just a works/doesn't check: the plan's fallback
is to *drop OCR from 1.0* if results embarrass the app, and the upgrade path is the standard
`tessdata` model (~15 MB instead of ~4 MB).

> **Unblocked 2026-08-15 — re-run this section from scratch on freshly taken photos.** The first
> attempt was invalidated by [#14](https://github.com/TheAmericanMaker/Tickbox/issues/14): every
> camera photo was stored rotated, so the boxes were measuring that bug rather than OCR quality.
> #14 is fixed and verified, but **photos attached before the fix are still wrong and cannot be
> corrected** — retake them rather than re-extracting from the ones already on the note.
>
> What the first attempt established, which is the useful half and still stands:
>
> - On an **upright** image, `tessdata_fast` is **good** — a screenshot of dense UI text came back
>   near-perfect, including layout and punctuation. That is the one data point taken on an image
>   that never depended on an orientation tag, and it is encouraging for the keep/upgrade decision.
> - On a **90°** image, output degrades badly.
> - On a **180°** image, output is unusable and unmistakably inverted — a company name and street
>   address returned character-reversed and bottom-up.
>
> So the engine is probably fine and the pipeline feeding it is not. Re-run the whole section on
> corrected images before filling in the verdict box.

- [ ] Attach a photo of a **flat printed page** (book, letter). Extract. Expect near-perfect text.
- [ ] A **shopping receipt** (narrow columns, small type).
      *Attempted 2026-08-15 — stored on its side, output garbage. Blocked on #14.*
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

### What the second round found (2026-08-15)

Owner testing after the rotation fix, results in two pinned notes:

- **Screenshot of a todo list: excellent.** Full sentences, punctuation, `tok/s`, `GPU/RAM`,
  `run_bench.sh` — essentially perfect.
- **Photo of a printed packing slip: fails badly.** `Mid Michigan Mfg LLC / 5298 Drow Rd /
  Prescott MI 48756` came back as `Mid Michiga`. Eleven characters.

The note recording it is titled "narrow small letters", but the image says otherwise: that address
block is **large, crisp, high-contrast and upright**. Whatever is failing, it is not text size. What
differs from the screenshot is the *scene* — busy dark background, shadow gradient, curved paper.

**So the cause was isolated on device**, against the same image, same library, same model, with a
temporary diagnostic that crossed page-segmentation mode with input preprocessing:

| variant | result |
| --- | --- |
| `PSM_AUTO` (current default) | `Mid Michiga` — 11 chars |
| `PSM_SINGLE_BLOCK` | `Mid Michigan Mf \| 5298 Drow Re \| Prescott \| 4` — 42 chars |
| `PSM_SPARSE_TEXT` | worse than SINGLE_BLOCK |
| greyscale | negligible change |
| greyscale + contrast | `Mid Michigan \| 5298 Drow` — 22 chars |
| **2× upscale** | **catastrophic** — 384 characters of hallucinated noise |

Those first passes all pointed away from the obvious answers — greyscale and contrast barely moved
it, and a 2× upscale made it *dramatically worse*, which rules resolution out rather than in.
Swapping in the 15 MB `tessdata_best` model changed **nothing**: byte-identical failure, in 112 ms.

Two facts together gave it away: two very different models failing *identically*, and doing it far
too fast for a 1500×2000 page. Both mean the recogniser was never seeing most of the image.

**Root cause, from dumping `TessBaseAPI.getThresholdedImage()`** — the binarised page Tesseract
actually reads from. Tesseract applies a **global Otsu threshold**, one cut-off for the whole frame.
A phone photo of paper does not have one exposure: half the sheet was lit and half shadowed, so the
single threshold put lit paper on one side and shadowed paper on the other. The dump shows the page
rendered as a black region with the text *inverted* to white, and a blown-out white blob eating the
right-hand side. Every line dissolved exactly where it crossed that boundary — which is precisely
the truncation pattern in the table above.

Not the model, not the resolution, not the segmentation. The image was being destroyed before
recognition began.

**Fix: Sauvola local thresholding**, which thresholds against local mean and variance so a lighting
gradient stops mattering. Leptonica ships inside tesseract4android already, so it costs no
dependency and about 200 ms.

| image | before | after |
| --- | --- | --- |
| Packing slip (photo of paper) | `Mid Michiga` | `Mid Michigan Mfg LLC` / `9298 Drow Rd` / `Prescott MI 48756` |
| Todo list (photo of a screen) | already good | unchanged |

One digit is still wrong (`9298` for `5298`). The 15 MB model fixes exactly that one digit and
nothing else, which is not worth 11 MB.

**Sauvola is not a free win, and is not applied unconditionally.** On the photo *of a screen* it was
markedly worse than Tesseract's own thresholding — local thresholding mangles screen moiré and
anti-aliased glyphs. So both passes run and the better result wins, scored by how many tokens in the
output look like real words.

That scoring is deliberately not Tesseract's own confidence, and both of its signals were tried
first: `meanConfidence()` is an average over what was recognised, so it *rewards reading less* and
picked `Mid Michiga` over the full address; `wordConfidences()` counts internal candidate blobs
including discarded ones, and reported **192 words for 134 characters of output**. Pinned by
`ReadableWordCountTest`, using the real captured strings.

Note in passing: `saveFromUri` downscales with `inSampleSize`, which only halves in powers of two,
so a 4000px photo becomes **2000px** rather than the 2560 `MAX_DIMENSION` allows. Not what was
breaking this, but worth tightening.

**Verdict: keep `tessdata_fast`.** The model was never the problem. Re-run the boxes above on fresh
photos to confirm across more subjects, but the keep/upgrade/drop question is settled — there is no
case for the larger model, and none for dropping the feature.

---

## K. Drag-to-reorder — on device

Implemented 2026-08-15, key-based. The state-machine move is unit-tested; the gesture is not.

**Verified 2026-08-15 on a Galaxy Z Fold 5 (Android 14, cover display), debug build.** Every box
below was checked against the database rather than the screen — a correct reorder and a
delete-and-reinsert look identical in the UI, and only the row ids tell them apart.

- [x] Long-press the handle and drag an item one position. It lands exactly where dropped, and
      **stays** after backing out and reopening (positions are rewritten on save).
      *Passed. `positions` rewritten to match list order, and every row kept its id.*
- [x] Drag the top unchecked item to the bottom of the unchecked section, and bottom to top.
      *Passed both directions, ids preserved.*
- [x] With **checked items present**, drag an unchecked item downward past the "Add item" row —
      it must not enter the checked section, and nothing should crash or teleport.
      *Passed, and this is the box that actually proves the key-based design.* With one item
      checked, display order was `Bravo, Alpha, Foxtrot, (blank), Charlie` while list order was
      still `Bravo, Alpha, Charlie, Foxtrot, (blank)` — Charlie displays last but is list index 2.
      Dragging **display** row 2 moved Foxtrot, the row actually grabbed. An index-based
      implementation would have moved Charlie. This is the bug that kept the feature out of the
      original app, and it does not occur here.
- [x] Drag with a **blank item** in the middle of the list. The blank row moves like any other.
      *Passed — the blank participated in every drag above without special-casing.*
- [x] Drag an **indented** item — indentation travels with it.
      *Passed — `indentLevel=1` survived the move.*
- [x] Reorder, then press back within 2 seconds. The new order persists (the mid-save id
      write-back guard is exactly this scenario).
      *Passed — backed out at +0s, inside the 2s debounce. Order persisted, no id churn.*
- [x] Reorder while the numbered labels are visible — numbering re-flows to match the new order.
      *Passed — renumbered 1, 2, 3 with the indented row correctly unnumbered.*
- [ ] The row visibly lifts (highlight) while dragged, and drops cleanly.
      *Not verifiable over adb: `input swipe` gives no window to capture mid-gesture. Needs eyes.*
- [~] TalkBack: the handle announces "Reorder" and is now a real, functioning control. (Gesture
      alternatives for accessibility are a known gap — note what TalkBack offers, if anything.)
      *Half done: the handle carries `contentDescription = "Reorder"`, confirmed in the
      accessibility tree, so TalkBack has something to announce. Whether it can actually **perform**
      a reorder is untested and needs a human with TalkBack on.*
- [x] Checked items have **no** drag handle.
      *Passed — 5 rows, 4 handles, and the checked row had none.*

---

## Reporting back

For anything that fails, the useful details are: which box, what you expected, what happened, and
whether it reproduces in debug, release, or both. Logcat filtered to the app helps. Note whether it
is a **port defect** (Smart Toolkit did this correctly) or **inherited** (the original did it too) —
that distinction decides whether it blocks 1.0.
