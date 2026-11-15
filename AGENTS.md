# Agent notes

Lumora is a Java Android TV launcher for Fire TV (`com.firestick.aura.launcher`). README.md
covers features, build, CI and the project layout.

## Build and test

Use JDK 21. Gradle 8.13 does not run on JDK 25.

```powershell
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

## Releasing

A push to `main` is a release. CI (`.github/workflows/android-build.yml`) builds the signed APK
and publishes the GitHub release `build-<versionCode>` with `update.json`, the feed installed
launchers update from. Version code = 1100 + CI run number.

1. Before pushing a release, bump `versionName` in `app/build.gradle` and the version in the
   README title, and add a new top `## <version>` section to `CHANGELOG.md`. That section becomes
   the release notes and the in-app update notes. Keep it under 1200 characters; the app
   truncates longer notes.
2. Push to `main` without `[skip ci]`. Put `[skip ci]` in the message only for commits that must
   not be released: they run no CI and get no run number.
3. Wait for the commit's CI run:
   `gh run list --repo engziada/Lumora --workflow android-build.yml --limit 3`.
   If it succeeded, the release is out. If tests or lint failed, fix the code and push again.
4. If the run failed without publishing (for example GitHub Actions is unavailable), publish
   locally with `tools/publish-release.ps1`. It uses the failed run's number for the version code.
   1. Run the dry run: `pwsh -NoProfile -File tools/publish-release.ps1`.
   2. Show the user the summary (tag, version, APK size, SHA-256, signer) and ask them to confirm.
   3. Only after the user confirms, run
      `pwsh -NoProfile -File tools/publish-release.ps1 -SkipChecks -Publish`.
5. Never re-run a CI run that was published locally. It would build the same version code and
   fail on the existing tag.

## Signing secrets

- The release keystore is `$HOME\lumora-release.jks`. The script reads its password from the
  DPAPI-encrypted file `$HOME\lumora-ks.sec`.
- Never ask for the keystore password in chat. Never print, log, store or commit it.
- If `lumora-ks.sec` is missing, ask the user to create it in their own terminal with the command
  in the script help (`Get-Help .\tools\publish-release.ps1 -Full`).
