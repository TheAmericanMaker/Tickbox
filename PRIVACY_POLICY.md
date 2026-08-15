# Privacy Policy

**Last updated:** August 15, 2026

Tickbox is a local-first Android app. There is no backend, no account system, and no
analytics. Your notes, checklists, and attachments stay on your device.

**The app declares no network permission.** This is verifiable: read
`app/src/main/AndroidManifest.xml` in the source repository, or inspect the installed
APK. An app without the INTERNET permission cannot transmit data itself.

## Data stored on your device

So the app works across launches, it stores locally:

- notes, checklist items, and photo attachments
- settings (theme, one-time hint acknowledgements)

If Android device backup is enabled, the app's database, settings, and note attachments
may be included in Android's backup or device-transfer systems, according to the app's
backup rules. That data is handled by Android, under Google's policies, not by Tickbox.

## Features that involve other software on your device

- **Voice input:** dictation uses your device's speech recognition provider via the
  standard Android speech interface. Spoken audio and transcripts may be processed by
  that provider — on most devices, Google — under its own privacy policy. Tickbox shows
  a one-time disclosure before the first use. See the
  [Google Privacy Policy](https://policies.google.com/privacy) if your device uses
  Google's recognizer.
- **Text extraction from photos (OCR):** runs entirely on your device, using the
  Tesseract engine bundled with the app. No image or text leaves the device.

## Permissions

- **Camera** — taking photos to attach to notes. Requested only when you use it.

That is the complete list.

## Data sharing

- No data is sold, and none is collected to sell.
- Data leaves your device only when you explicitly share or export something, or when
  you use voice input handled by your device's speech provider.

## Your choices

- Revoke the camera permission any time under **Settings → Apps → Tickbox → Permissions**.
- Clear app data or uninstall to remove everything the app stores.
- Disable Android device backup if you don't want app data included in system backups.
- Use **Export notes** to get a complete copy of your data as a ZIP archive, any time.

## Children's privacy

Tickbox is not directed at children under 13 and collects no personal information from
anyone, children included — there is nowhere for it to go.

## Changes to this policy

The effective date above changes when the policy materially changes, and the policy's
history is public in the repository's git log.

## Contact

Questions: open an issue at https://github.com/TheAmericanMaker/Tickbox/issues.
