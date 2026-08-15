# Changelog

All notable changes to Tickbox. Format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [SemVer](https://semver.org/).

## [Unreleased]

Initial release in preparation. Tickbox is the notepad from
[Smart Toolkit](https://github.com/TheAmericanMaker/Smart-Toolkit), extracted into a
standalone, GPL-3.0 app.

### Added
- Text notes and checklists, with conversion between the two
- Checklists: indent levels, auto-numbering, five bullet styles, a checked-items
  section, and **drag-to-reorder**
- Checklist progress on the note list ("3 of 8 done")
- Search, type filters, pinning, colour labels
- Photo attachments (gallery and camera) with a pinch-zoom viewer
- On-device text extraction from photos (Tesseract; fully offline)
- Voice dictation via the system speech recognizer, with a one-time privacy disclosure
- Note templates and keyword suggestions
- Share as text and HTML
- Full ZIP export/import, compatible with Smart Toolkit's notepad backups
- Undo window for deletions; autosave

### Fixed (relative to the Smart Toolkit notepad)
- Colour labels are no longer lost on export/import round trips
- Checklist rows keep their identity across autosaves instead of being recreated
  every two seconds
- Deleting a note now also deletes its image files; orphaned images from earlier
  versions are cleaned up at startup
- Opening a note without changing it no longer rewrites it (and no longer reorders
  the list by "last opened")
- Two rapid saves can no longer duplicate a brand-new note
- Camera capture no longer races the file copy
- Converting a checklist to a text note shows the converted text immediately
- Shared HTML escapes note text (`A & B <3` renders literally)
- OCR failures show a message instead of doing nothing silently

### Security
- No network permission at all; the only runtime permission is Camera
- Backup import hardened against path traversal, zip bombs, and truncated archives
  (carried over from Smart Toolkit and extended with an archive version guard)
