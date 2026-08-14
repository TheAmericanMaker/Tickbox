# Tickbox

A simple, local-first notes and checklists app for Android.

No accounts. No cloud. No tracking. **The app declares no network permission at all** — you can
verify that yourself in [`AndroidManifest.xml`](app/src/main/AndroidManifest.xml).

> **Status: in development, not yet tested on a device.** Tickbox has been extracted from the
> notepad in [Smart Toolkit](https://github.com/TheAmericanMaker/Smart-Toolkit) and builds
> cleanly, but it has not been run on real hardware and has no test suite yet. Don't trust it
> with data you can't afford to lose.
>
> Contributors: start with [CLAUDE.md](CLAUDE.md) and
> [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Planned for 1.0

- Text notes and checklists, with lossless conversion between the two
- Checklists with indent levels, auto-numbering, five bullet styles, and drag-to-reorder
- Search, filtering, pinning, and colour labels
- Photo attachments, with on-device text extraction (engine not yet wired up)
- Voice dictation via the system speech recogniser
- Note templates
- Full ZIP backup: export and import everything, including images

## Building

Requires JDK 17 and the Android SDK (Platform 35). No signing key is needed for a debug build.

```bash
git clone https://github.com/TheAmericanMaker/Tickbox.git
cd Tickbox
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Architecture

Deliberately small and boring, so it stays approachable:

- Single Gradle module, single activity, 100% Jetpack Compose
- Room for notes, DataStore for preferences, images as files in app-private storage
- Manual dependency injection via an `AppContainer` on the Application class — no Hilt, no Dagger
- Two screens: a note list and a note editor

## License

[GPL-3.0-or-later](LICENSE). Contributions are accepted under the same license.
