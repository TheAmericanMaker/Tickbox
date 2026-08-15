# Security Policy

## Reporting

Please report vulnerabilities privately via
[GitHub Security Advisories](https://github.com/TheAmericanMaker/Tickbox/security/advisories/new)
rather than a public issue. You can expect an acknowledgement within a week. Please
allow up to 90 days for a fix before public disclosure.

## Scope — what is interesting here

Tickbox is a local-first app with **no network permission**, no accounts, and no IPC
surface beyond a single exported launcher activity. The realistic attack surface is
small and specific:

- **The backup importer** (`data/backup/NoteImportArchive.kt`) parses untrusted ZIP
  archives. It already defends against path traversal, absolute paths, backslashes,
  directory entries, duplicate entries, oversized entries, and zip bombs, and stages to
  a scratch directory before touching real storage — bypasses of any of that are
  exactly what we want to hear about.
- **The FileProvider** grant surface used for camera capture.
- **The OCR path**: Tesseract and Leptonica parse untrusted image files in native code.
  A crafted image that crashes or corrupts memory through `setImage`/recognition is in
  scope, though the fix may belong upstream.

Out of scope: anything requiring a compromised device, and the documented behaviour
that importing an archive twice duplicates notes.

## Supported versions

The latest release only.
