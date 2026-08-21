# Changelog

All notable changes to Tickbox. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [SemVer](https://semver.org/).

## [1.0.0] - 2026-08-20

First release. Tickbox is the notepad from
[Smart Toolkit](https://github.com/TheAmericanMaker/Smart-Toolkit), extracted into a
standalone, GPL-3.0 app.

### Added
- Text notes and checklists, with conversion between the two that keeps indentation
  and remembers ticked items
- Checklists: one indent level, five tick styles, a collapsible checked-items
  section, **drag-to-reorder** by the grip, and **indent by dragging the tick box**
- Checklist progress on the note list ("3 of 8 done", "All 8 done")
- Search, type filters, pinning, colour labels
- Photo attachments (gallery and camera) with a full-screen viewer
- On-device text extraction from photos (Tesseract; fully offline)
- Voice dictation via the system speech recognizer, with a one-time privacy disclosure
- Note templates
- Share as text and HTML
- Full ZIP export/import, compatible with Smart Toolkit's notepad backups
- Undo window for deletions; autosave with a debounce and a ceiling
- Appearance setting — Light, Dark or System
- Help & about screen, covering the gestures that have no visible affordance
- **Two build variants.** `withOcr` is the full app; `noOcr` drops Tesseract and the
  language model, which is 31 MB of a 31.5 MB download. Per-ABI APKs as well, so an
  arm64 phone downloads 10.5 MB rather than 31 MB.

### Fixed (relative to the Smart Toolkit notepad)
- Colour labels are no longer lost on export/import round trips
- Checklist rows keep their identity across autosaves instead of being recreated
  every two seconds
- Deleting a note now also deletes its image files; orphaned images from earlier
  versions are cleaned up at startup
- Opening a note without changing it no longer rewrites it (and no longer reorders
  the list by "last opened")
- Two rapid saves can no longer duplicate a brand-new note
- Camera capture no longer races the file copy, and survives a rotation or a fold
- Converting a checklist to a text note shows the converted text immediately
- Shared HTML escapes note text (`A & B <3` renders literally)
- OCR failures show a message instead of doing nothing silently
- Photo EXIF orientation is applied on import, so sideways photos no longer break
  text extraction
- Sustained typing reaches disk: the autosave debounce is capped, rather than being
  reset by every keystroke
- Pressing Enter at the end of a long checklist focuses the new row instead of
  appending to the previous one
- Backing out of the editor finishes its save even though navigating away destroys
  the screen that started it
- Text extraction picks the better of its two thresholding passes by counting words
  it was *confident* of, so a screenshot no longer scores worse than the noise from
  a mis-binarised copy of itself

### Removed
- Checklist keyword suggestions. A hardcoded English map produced generic office
  filler, took about a third of the editor, and never scrolled away.

### Security
- No network permission at all; the only runtime permission is Camera
- Backup import hardened against path traversal, zip bombs, and truncated archives
  (carried over from Smart Toolkit and extended with an archive version guard)
