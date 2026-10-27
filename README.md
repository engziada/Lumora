# Z TV Launcher — v4.0.0

![Android CI](https://github.com/engziada/Lumora/actions/workflows/android-build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-8B7CFF)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%2F%20Fire%20OS-00A8E1)

A lightweight, ad-free launcher for Fire TV and Android TV. Java only, no analytics SDK and no web views.

## Features

- Greeting hero, featured tiles, plated app icons and TV-friendly D-pad navigation.
- Home, Apps, Cinema and Settings tabs.
- Pinned favourites, hidden apps and independent ordering within each row.
- Offline generated wallpaper scenes.
- Optional online publisher logos and online wallpaper stills; both are disabled by default.

## Network access

Z TV does not send analytics or telemetry. Network-backed visual features are opt-in and disabled by default:

- HD logos request publisher favicons from `google.com/s2/favicons` and cache them locally.
- Online wallpapers request still images from `images.unsplash.com` and cache them locally.

When either feature is off, its corresponding endpoint is not contacted. Installed app icons and generated offline scenes remain available without network access.

## Build

The source and target compatibility level is Java 17. Use JDK 21 to run Gradle 8.13 and AGP 8.4.2; the repository does not pin a machine-specific JDK path in `gradle.properties`.

```powershell
$env:JAVA_HOME = '<path-to-jdk-21>'
.\gradlew.bat lintDebug assembleDebug assembleRelease
```

Bash:

```bash
export JAVA_HOME='<path-to-jdk-21>'
./gradlew lintDebug assembleDebug assembleRelease
```

Debug output: `build-out/app/outputs/apk/debug/app-debug.apk`

## Install

```powershell
$adb = '<path-to-adb>'
& $adb connect '<device-ip>:5555'
& $adb install -r 'build-out\app\outputs\apk\debug\app-debug.apk'
& $adb shell am start -n 'com.firestick.aura.launcher/.MainActivity'
```

Making Z TV the permanent HOME app may be blocked on Fire OS. Protected Amazon packages require ADB, Device Owner or root. The app displays equivalent ADB commands but never executes privileged package-manager commands.

## Project layout

```text
app/src/main/java/com/firestick/aura/launcher/
├── MainActivity.java
├── SettingsActivity.java
├── adapter/
├── manager/
└── model/
```

## Continuous integration

`.github/workflows/android-build.yml` validates the Gradle wrapper, runs `lintDebug`, assembles release and debug builds with JDK 21, and uploads the debug APK.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE). Bundled fonts, AndroidX and the Gradle wrapper retain their own licenses; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
