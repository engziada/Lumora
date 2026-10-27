# Lumora — v5.1.0

![Android CI](https://github.com/engziada/Lumora/actions/workflows/android-build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-E3A15A)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%2F%20Fire%20OS-8C5A34)

*Your world on a brighter screen.* A lightweight, ad-free home screen for Fire TV and Android TV.
Java only, no analytics SDK and no web views. The application id stays `com.firestick.aura.launcher`
so existing installs upgrade in place.

## Features

- **Home**: one strip of wide glass cards at the bottom of the screen over a live, warm-graded
  wallpaper; the focused app is shown as a large title.
- **Shows**: today's broadcast TV and streaming releases for a chosen country, as posters with
  channel chips. *Watch on <app>* opens the app on a search for the title; missing apps open their
  Amazon Appstore page.
- **Apps** / **Streaming**: full grids. Any app can be moved between the two or added to Home.
- **Quick**: Recent, Clear memory, Screenshot, Restart, Wi-Fi.
- **Gear** (next to the clock): Lumora settings or Fire TV settings.
- App menu (long-press or Menu): Open, Move, Add to/Remove from Home, Move to Apps/Streaming,
  Lock with code, Hide, App details, Uninstall.
- **Move**: choose Move once, then the arrows move the app until OK or Back.
- **App lock**: an arrow-key code (4–8 directions) for chosen apps and, optionally, Lumora settings.
- Themes: Amber, Copper, Rose, Moonlight. The theme colours the wallpaper grade, focus rings and icons.
- HD app icons from dashboard-icons, falling back to the installed icon.

## One-time setup: quick actions and the Home button

Fire OS always opens Amazon's home on the Home button, ignores third-party home apps, and hides
accessibility switches for apps outside the Appstore. Lumora's accessibility service (*Lumora Quick
Actions*) handles Screenshot, Restart and returning to Lumora when the Fire TV home appears. To
turn it on, grant one permission once from a computer (Developer options › ADB debugging on):

```powershell
$adb = '<path-to-adb>'
& $adb connect '<device-ip>:5555'
& $adb shell pm grant com.firestick.aura.launcher android.permission.WRITE_SECURE_SETTINGS
```

Lumora then enables its own service, adding it to any services already enabled, and re-enables it
if Fire OS turns it off. The service receives window events only from `com.amazon.tv.launcher`
and `com.amazon.firehomestarter` and never reads screen content.

Limits:

- After Home, the Fire TV home can show briefly before Lumora returns.
- Restart opens the system power menu; a direct reboot needs a system signature.
- Locked apps are protected only when opened from Lumora, not from Fire TV menus or voice.
- Recent lists apps opened from Lumora only.

## Network access

Lumora sends no analytics or telemetry. It contacts these hosts only for the listed features:

| Host | Feature | Default | Turn off in |
|------|---------|---------|-------------|
| `cdn.jsdelivr.net` (homarr-labs/dashboard-icons) | HD app icons and their name index | On | Settings › Appearance › App icons |
| `api.tvmaze.com`, `static.tvmaze.com` | Shows tab: schedules and posters | On | Settings › TV listings |
| `cdn.esahubble.org` | Space wallpapers (nebulae, galaxies) | On | Settings › Wallpaper |
| `images.unsplash.com` | Night sky and desert wallpapers (1280 x 720) | On | Settings › Wallpaper |

Downloads are cached on the device (icons indefinitely, schedules 3 hours, posters 14 days,
wallpapers until the cache is cleared). With every feature off, Lumora makes no network requests.

**Movie posters:** TVmaze covers TV and streaming series only. Film posters would need TMDB, which
requires a free account and API key; it is not integrated.

## Launcher icon on Fire TV

For sideloaded apps, Fire OS builds the *Your apps and channels* tile from the square app icon and
letterboxes it; it ignores `android:banner`. Full-width tiles such as Tubi's come from Amazon
Appstore listing art. Lumora's icon is therefore full-bleed so the letterboxed tile looks
intentional. Fire OS caches the tile until the app is uninstalled and reinstalled.

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

Protected Amazon packages can only be removed over ADB, Device Owner or root. Settings › ADB tools
lists the commands; Lumora never runs privileged package-manager commands itself.

## Project layout

```text
app/src/main/java/com/firestick/aura/launcher/
├── MainActivity.java       Home, Shows, Apps, Streaming, gear menu, move mode
├── QuickMenu.java          Quick menu
├── ShowDetails.java        Show details, watch-in-app, Appstore hand-off
├── SettingsActivity.java
├── adapter/                App tiles and cards, posters
├── manager/                Apps, icons, shows, images, wallpapers, lock, preferences, quick access
├── model/
├── service/QuickActionService.java
└── ui/                     Glass overlays, glass menu, lock pad
brand/                      Visual identity, palette, logo SVGs
```

## Continuous integration

`.github/workflows/android-build.yml` validates the Gradle wrapper, runs `lintDebug`, assembles
release and debug builds with JDK 21, and uploads the debug APK.

## Credits

Listings and posters: [TVmaze](https://www.tvmaze.com) (CC BY-SA 4.0). App logos:
[dashboard-icons](https://github.com/homarr-labs/dashboard-icons) (Apache-2.0). Space wallpapers:
ESA/Hubble (CC BY 4.0). Night sky and desert wallpapers: Unsplash. Type: Poppins (SIL OFL 1.1).

Made by Zakaa — support@zakaa-ai.net — https://zakaa-ai.net

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE). Bundled fonts, AndroidX and the Gradle wrapper retain their own
licenses; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
