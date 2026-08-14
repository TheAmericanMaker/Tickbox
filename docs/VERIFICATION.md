# Verification checklist

What CI cannot tell us. Everything here needs a device or emulator (API 26+).

Nothing in this document has been run yet. The extraction was verified only to the extent that it
compiles, lints, and packages. Treat every box below as genuinely unknown, and record what you find
— a failure here is expected and useful, not a surprise.

Suggested order: **A first** (it guards real data), then **B** (it is where the new code is), then
the rest.

---

## A. Migration from Smart Toolkit

The whole point of the extraction. Do this before anything destructive.

**Before you start:** export from Smart Toolkit **twice, to two separate destinations** — cloud and
PC, not just internal storage. That archive is the only copy of the data and also the fixture for
the regression test.

**Check the size first.** The importer rejects archives over **64 MB**, more than **200 images**, or
more than **5 images on any one note**. It surfaces as a generic "Backup file is too large."
If the real export exceeds any of these, raise `MAX_IMPORT_TOTAL_BYTES` (and friends) in
`data/backup/NoteImportArchive.kt` before trying — they are client-side sanity limits, not the
security boundary. The per-entry caps are what stop zip bombs; leave those.

- [ ] Smart Toolkit → notes → ⋮ → **Export**. Save the ZIP off-device, twice.
- [ ] Install Tickbox **debug** (`.debug` suffix, so it coexists with everything).
- [ ] Tickbox → ⋮ → **Import**, pick the ZIP. It reports a note and image count.
- [ ] Note **count** matches.
- [ ] The **longest checklist** is intact: item text, order, checked state, indent levels.
- [ ] **Pinned** notes are pinned, and sort to the top.
- [ ] **Icon styles** survived (checkbox / circle / star / heart / square).
- [ ] **Timestamps** survived — created and updated dates look right, not "now".
- [ ] Every **image-bearing note** still has its images, in the right order, on the right note.
- [ ] **Colour labels are absent.** This is correct and expected: Smart Toolkit's exporter never
      wrote the field, so that data was already lost before Tickbox saw it. Re-apply by hand.
- [ ] Emoji and non-ASCII text in titles round-trip cleanly.

**Then prove the format is now lossless in both directions:**

- [ ] Set a colour label on a few notes in Tickbox.
- [ ] Export from Tickbox → clear app data → import that archive.
- [ ] Everything above survives, **including colour labels** this time.

**Keep Smart Toolkit installed for at least two weeks.** Different applicationIds mean separate
databases and separate storage; uninstalling it is irreversible.

---

## B. Checklist persistence — the riskiest new code

`NoteRepository.saveNote` was rewritten to reconcile rows instead of deleting and reinserting the
whole checklist on every autosave. `NoteEditViewModel` has to carry ids in and read generated ids
back for that to work. Neither half has run.

- [ ] Create a checklist, add several items, back out, reopen — all items present, in order.
- [ ] Reopen, edit **one** item's text, wait past the 2s autosave, back out, reopen — only that
      item changed.
- [ ] Add an item in the middle, save, then edit a **different** item. The edit lands on the item
      you meant, not a neighbour.
- [ ] Delete an item, save, reopen — it stays deleted, and nothing else shifted.
- [ ] Indent an item, save, reopen — indentation persisted.
- [ ] Check an item; it moves into the "N checked items" section. Reopen — still checked, still
      there.
- [ ] Uncheck it; it returns to the main list.
- [ ] Type continuously for ~30 seconds in one item. Autosave fires repeatedly during this; when
      you reopen, the text should be complete and the list unchanged. **If item identity is
      breaking, this is where it shows.**
- [ ] Switch a checklist to a text note and back. Item text survives the round trip (checked state
      and indentation are expected to be dropped — that conversion is lossy by design).

---

## C. Autosave and back behaviour

- [ ] Type a title only, press back, reopen the list — the note is saved.
- [ ] Open a **new** note, type nothing, press back — no empty note is created.
- [ ] Type, then press back **before** 2 seconds elapse — the note still saves (back forces a save).
- [ ] Predictive back / gesture back behaves the same as the top-bar arrow.

---

## D. Images

- [ ] Attach from gallery. Thumbnail appears.
- [ ] Take a photo. Camera permission is requested on first use; denying it fails gracefully.
- [ ] Attach 5 images, then try a 6th — it is refused rather than silently dropped.
- [ ] Tap an image — full-screen viewer opens, pinch-zoom and pan work.
- [ ] There is **no "Extract text" button** and no OCR badge. Correct until Tesseract lands.
- [ ] Remove an image — it disappears, and the file is gone from disk:
      `adb shell run-as com.theamericanmaker.tickbox.debug ls files/note_images`
- [ ] Delete a note that has images. Wait past the 5s undo window. The files are gone from
      `note_images/`. **This is the orphan cleanup working.**
- [ ] Then relaunch the app and confirm the startup sweep did **not** delete anything still in use.
      It skips files under 24h old, so also check against an older library if you can.

---

## E. Sharing

- [ ] Share a text note. Both plain text and formatted HTML arrive intact in the receiving app.
- [ ] Share a checklist. Numbering, ☐/☑ boxes, indentation and strikethrough all render.
- [ ] **Create a note titled `A & B <3` and share it to an HTML-aware target (Gmail).** The title
      renders literally, with no broken markup. This was a real bug in the original.

---

## F. List screen

- [ ] Empty library shows "No notes yet. Tap + to write your first one."
- [ ] Search with no matches shows a **different** message naming the query.
- [ ] Filtering to Checklists with none shows a **third** message. (All three used to be the same
      text, which read as the search having broken.)
- [ ] Swipe a note left — it deletes with an Undo snackbar; Undo restores it.
- [ ] Let the snackbar expire — the note is really gone.
- [ ] Delete a note, and while the snackbar is still showing, delete a **second** one. The first
      commits immediately; only the second is undoable.
- [ ] Pin and unpin; pinned notes sort to the top under a "Pinned" header.
- [ ] Colour labels tint the note cards, and the card stays opaque while swiping (no red bleed
      through from the delete background).

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
- [ ] Toggle system dark mode with the app open — the theme follows.
- [ ] On Android 12+, colours follow the system wallpaper (Material You).
- [ ] Rotate the device mid-edit — text, caret and checklist state survive.
- [ ] The launcher icon renders correctly, including themed/monochrome mode on Android 13+.
      It is currently **placeholder art** and needs replacing before release.

---

## I. Release build

- [ ] `./gradlew assembleRelease` succeeds. It is minified with `isShrinkResources = true` and
      currently **unsigned** — signing arrives in Phase 10.
- [ ] Install that APK and repeat at least sections A and B. **R8 has never been exercised against
      this code**, and Room plus reflection is exactly where shrinking tends to break. If something
      works in debug and not in release, suspect `proguard-rules.pro`.

---

## Reporting back

For anything that fails, the useful details are: which box, what you expected, what happened, and
whether it reproduces in debug, release, or both. Logcat filtered to the app helps. Note whether it
is a **port defect** (Smart Toolkit did this correctly) or **inherited** (the original did it too) —
that distinction decides whether it blocks 1.0.
