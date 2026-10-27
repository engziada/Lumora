# Lumora — v5.0.0

![Android CI](https://github.com/engziada/Lumora/actions/workflows/android-build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-E3A15A)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%2F%20Fire%20OS-8C5A34)

*Your world on a brighter screen.* A lightweight, ad-free home screen for Fire TV and Android TV.
Java only, no analytics SDK and no web views. The application id stays `com.firestick.aura.launcher`
so existing installs upgrade in place.

## Features

- Warm espresso and amber identity with a vector logo (`ic_lumora_mark`, `ic_lumora_wordmark`) and a TV banner.
- Tabs: **Home**, **Shows**, **Apps**, **Streaming**, plus **Quick** (menu) and **Settings**.
- **Shows**: today's TV schedule for a chosen country plus streaming releases, as poster cards with
  channel chips (All / On TV / Streaming / top channels). A details dialog offers *Open in <app>*
  when a matching streaming app is installed.
- **Quick menu**: Recent apps, Clear memory, Screenshot, Sleep, Restart, Power off, Wi-Fi, Bluetooth.
- **HD app icons** from the open dashboard-icons collection, downloaded on demand and cached; apps
  without a match keep their installed icon.
- Pinned favourites, hidden apps and per-row ordering.
- Offline generated wallpaper scenes; optional online stills.
- Accents: Amber (default), Copper, Rose, Moonlight.

## Network access

Lumora sends no analytics or telemetry. It contacts these hosts only for the listed features:

| Host | Feature | Default | Turn off in |
|------|---------|---------|-------------|
| `cdn.jsdelivr.net` (homarr-labs/dashboard-icons) | HD app icons and their name index | On | Settings › Appearance › App icons |
| `api.tvmaze.com`, `static.tvmaze.com` | Shows tab: schedule and posters | On | Settings › TV listings |
| `images.unsplash.com` | Online wallpaper stills (1280 x 720) | Off | Settings › Wallpaper › Source |

All downloads are cached on the device (icons indefinitely, schedules for 3 hours, posters for 14 days).
With every feature off, Lumora makes no network requests.

**Movie posters:** TVmaze covers TV and streaming series only. Film posters would need TMDB, which
requires a free account and API key; it is not integrated.

## Quick menu permissions

Screenshot, Sleep, Restart and Power off use an accessibility service (*Lumora Quick Actions*) that
only triggers system actions and never reads screen content. On first use Lumora explains this and
opens Accessibility settings. If Fire OS hides third-party services there, enable it over ADB:

```powershell
& $adb shell settings put secure enabled_accessibility_services com.firestick.aura.launcher/com.firestick.aura.launcher.service.QuickActionService
& $adb shell settings put secure accessibility_enabled 1
```

The first command replaces any other enabled accessibility services. Limits:

- Restart and Power off open the system power dialog; a direct reboot needs a system signature.
- Screenshot and Sleep need Android 9 (API 28) or newer.
- Recent apps lists apps launched from Lumora only (no usage-access permission is requested).
- Clear memory asks Android to stop cached background processes and reports the measured change.

## Build

The source and target compatibility level is Java 17. Use JDK 21 to run Gradle 8.13 and AGP 8.4.2;
the repository does not pin a machine-specific JDK path in `gradle.properties`.

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

Making Lumora the permanent HOME app may be blocked on Fire OS. Protected Amazon packages require
ADB, Device Owner or root. Settings › ADB tools lists the equivalent commands; the app never runs
privileged package-manager commands itself.

## Project layout

```text
app/src/main/java/com/firestick/aura/launcher/
├── MainActivity.java       Home, Shows, Apps, Streaming tabs
├── QuickMenu.java          Quick menu and accessibility prompt
├── SettingsActivity.java
├── adapter/                Rows, featured strip, poster grid
├── manager/                Apps, icons, shows, images, wallpapers, preferences
├── model/
└── service/QuickActionService.java
brand/                      Visual identity, palette, logo SVGs
```

## Continuous integration

`.github/workflows/android-build.yml` validates the Gradle wrapper, runs `lintDebug`, assembles
release and debug builds with JDK 21, and uploads the debug APK.

## Credits

Listings and posters: [TVmaze](https://www.tvmaze.com) (CC BY-SA 4.0). App logos:
[dashboard-icons](https://github.com/homarr-labs/dashboard-icons) (Apache-2.0). Online wallpapers:
Unsplash. Type: Poppins (SIL OFL 1.1).

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE). Bundled fonts, AndroidX and the Gradle wrapper retain their own
licenses; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
