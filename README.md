# Tickbox

[![CI](https://github.com/TheAmericanMaker/Tickbox/actions/workflows/ci.yml/badge.svg)](https://github.com/TheAmericanMaker/Tickbox/actions/workflows/ci.yml)
[![License: GPL-3.0-or-later](https://img.shields.io/badge/License-GPL--3.0--or--later-blue.svg)](LICENSE)

A simple, local-first notes and checklists app for Android.

No accounts. No cloud. No tracking. **The app declares no network permission at all** — you can
verify that yourself in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).

> **Status: release candidate under device testing.** Extracted from the notepad in
> [Smart Toolkit](https://github.com/TheAmericanMaker/Smart-Toolkit) and rebuilt as a standalone
> app. First release lands once [docs/VERIFICATION.md](docs/VERIFICATION.md) is worked through.
>
> Contributors: start with [CLAUDE.md](CLAUDE.md) and [CONTRIBUTING.md](CONTRIBUTING.md).

## Features

- Text notes and checklists, with conversion between the two
- Checklists that behave like the main feature they are: indent levels, auto-numbering,
  five bullet styles, a tidy checked-items section, **drag-to-reorder**, and progress
  ("3 of 8 done") right on the note list
- Search, type filters, pinning, colour labels
- Photo attachments with a pinch-zoom viewer, and **on-device text extraction**
  (Tesseract — nothing leaves the phone)
- Voice dictation via the system speech recognizer, disclosed before first use
- Note templates, share as text or HTML
- Full ZIP backup: export and import everything, including images

## Privacy

The manifest requests exactly one permission: **Camera**, for photo attachments, asked
only when used. There is no INTERNET permission, so the app is incapable of phoning
home. Details in [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

## Building

Requires JDK 17 and Android SDK Platform 35. No signing key needed for a debug build.

```bash
git clone https://github.com/TheAmericanMaker/Tickbox.git
cd Tickbox
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`. If Gradle fails with a bare version
string like `25.0.4`, your Gradle daemon is running on a too-new JVM — see
[CONTRIBUTING.md](CONTRIBUTING.md).

## Architecture

Deliberately small and boring, so it stays approachable:

- Single Gradle module, single activity, 100% Jetpack Compose
- Room for notes, DataStore for preferences, images as files in app-private storage
- Manual dependency injection via an `AppContainer` on the Application class — no Hilt
- Two screens: a note list and a note editor
- The whole test suite runs on the JVM: `./gradlew testDebugUnitTest`, no emulator

## Third-party

- [tesseract4android](https://github.com/adaptech-cz/Tesseract4Android) (Apache-2.0) —
  OCR engine, fetched from JitPack
- [tessdata_fast](https://github.com/tesseract-ocr/tessdata_fast) `eng` model
  (Apache-2.0) — bundled in assets
- [Reorderable](https://github.com/Calvin-LL/Reorderable) (Apache-2.0) — drag-to-reorder
- AndroidX / Jetpack Compose (Apache-2.0)

## License

[GPL-3.0-or-later](LICENSE). Contributions are accepted under the same license.
