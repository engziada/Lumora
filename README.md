# Z TV Launcher — v4.0.0 (NOVA-style redesign)

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
- Poppins Regular / Medium / SemiBold under the SIL Open Font License.

## Network access

Z TV does not send analytics or telemetry. Network-backed visual features are opt-in and disabled by default:

- HD logos request publisher favicons from `google.com/s2/favicons` and cache them locally.
- Online wallpapers request still images from `images.unsplash.com` and cache them locally.

When either feature is off, its corresponding endpoint is not contacted. Installed app icons and generated offline scenes remain available without network access.

## Build

JDK 17+ (the build is pinned to JDK 21 in `gradle.properties`; JDK 25 breaks Groovy build scripts).

```powershell
.\gradlew.bat assembleDebug
```

Output: `build-out\app\outputs\apk\debug\app-debug.apk`

## Install

```powershell
$adb = 'E:\Firestick-Cleanup-Windows-WinForm-v1.3.0\adb\adb.exe'
& $adb connect 192.168.100.111:5555
& $adb install -r build-out\app\outputs\apk\debug\app-debug.apk
& $adb shell am start -n com.firestick.aura.launcher/.MainActivity
```

Making it the permanent HOME app may be blocked on Fire OS. Protected Amazon packages require ADB, Device Owner or root. The app only displays the equivalent ADB commands; it does not execute privileged package-manager commands.

## Project layout

```text
app/src/main/java/com/firestick/aura/launcher/
├── MainActivity.java
├── SettingsActivity.java
├── adapter/
│   ├── AppRowAdapter.java
│   ├── FeaturedRowAdapter.java
│   └── CategoryRowAdapter.java
├── manager/
│   ├── AppRepository.java
│   ├── PreferenceManager.java
│   ├── IconService.java
│   ├── WallpaperManager.java
│   ├── DebloatManager.java
│   └── SoundManager.java
└── model/
```

## Continuous integration

`.github/workflows/android-build.yml` builds the debug APK on pushes to `main` and pull requests, then uploads it as the `ztv-launcher-debug` artifact with its SHA-256.

Locally, `gradlew.bat` uses the JDK selected by `JAVA_HOME`. If needed, configure a machine-specific JDK path in `%USERPROFILE%\.gradle\gradle.properties`; machine-specific paths must not be committed.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE). Bundled fonts, AndroidX and the Gradle wrapper retain their own licenses; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
