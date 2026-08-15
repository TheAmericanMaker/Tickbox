# Contributing to Tickbox

Thanks for considering it. Tickbox is deliberately small, and keeping it that way is a
feature — read the scope section before proposing anything large.

## Building

- **JDK 17** and **Android SDK Platform 35**.
- Heads-up: **Kotlin 2.1.0 cannot run under Java 25.** If Gradle fails with an error
  that is just a bare version string like `25.0.4`, your daemon JVM is too new — point
  `org.gradle.java.home` in `~/.gradle/gradle.properties` (not the committed file) at a
  JDK 17. Details in [CLAUDE.md](CLAUDE.md).

```bash
./gradlew assembleDebug        # debug APK
./gradlew testDebugUnitTest    # full test suite — JVM only, no emulator needed
./gradlew lintDebug            # Android lint
./gradlew ktlintFormat         # autoformat before committing
```

No signing key is needed for anything above. Debug builds install alongside a release
Tickbox (`.debug` applicationId suffix).

## Before you open a PR

1. `./gradlew ktlintFormat testDebugUnitTest lintDebug` passes locally.
2. New behaviour comes with a test where the layer allows it — the repository, backup,
   and formatter layers all run as plain JVM tests.
3. UI changes include a screenshot in the PR.
4. One change per PR. Reformat-only commits stay separate from behaviour commits.

## Things to know before touching certain areas

- **`data/backup/`** is a compatibility contract: archives must stay readable by and
  from the Smart Toolkit notepad this app was extracted from. Adding optional JSON
  fields is fine; renaming or restructuring is not. `NoteImportArchive` is the app's
  only untrusted-input surface — changes there are security-relevant.
- **Checklist item ids must survive saves.** See the reconciliation notes in
  `NoteRepository` and the regression test in `NoteRepositoryTest` before changing
  how saving works.
- **No Hilt, no Dagger.** The dependency graph is `AppContainer` — five lazy
  properties. It does not need a framework, and PRs introducing one will be declined.

## Scope

Tickbox aims to be the notes app for someone who wants Google Keep without an account.
The following are **permanently out of scope** — PRs adding them will be declined with
a pointer to this paragraph, however good the code is: markdown/rich text, reminders
and notifications, sync, accounts, cloud services, and collaboration. The ZIP export
is the sync story. `docs/POLISH.md` and the issue tracker hold the actual roadmap.

## Licensing

Contributions are accepted under **GPL-3.0-or-later**, the project license. New `.kt`
files start with the two SPDX header lines used everywhere else. There is no CLA.

## Conduct

The [Contributor Covenant](CODE_OF_CONDUCT.md) applies to all project spaces.
