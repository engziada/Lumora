# Lumora — v5.4.0

![Android CI](https://github.com/engziada/Lumora/actions/workflows/android-build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-E3A15A)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%2F%20Fire%20OS-8C5A34)

*Your world on a brighter screen.* A lightweight, ad-free home screen for Fire TV and Android TV.
Java only, no analytics SDK and no web views. The application id stays `com.firestick.aura.launcher`.

## Features

- **Home**: one strip of wide glass cards at the bottom of the screen over a live, warm-graded
  wallpaper; the focused app is shown as a large title.
- **Shows**: today's broadcast TV and streaming releases for a chosen country, as posters with
  channel chips. *Watch on <app>* opens the app on a search for the title; missing apps open their
  Amazon Appstore page.
- **Apps** / **Streaming**: full grids. Any app can be moved between the two or added to Home.
- **Quick**: Recent, Clear memory, Screenshot, Restart, Wi-Fi.
- **Gear** (next to the clock): Lumora settings or Fire TV settings.
- **Assistant** (OpenRouter, free models): floating button or **hold ☰ Menu** in any app. Speak
  through your phone: scan the QR code and use the phone keyboard's microphone.
- **TV control**: "pause", "volume up", "next episode", "علي الصوت" run instantly (no key needed);
  the assistant can also open apps, search inside them, open settings pages and change the theme.
  See [docs/ASSISTANT.md](docs/ASSISTANT.md).
- **Updates**: Lumora checks this repository's GitHub Releases (daily by default, configurable) and
  installs a verified new build after you confirm.
- **Device info**: live CPU, memory, storage, network and temperature readings.
- App menu (long-press or Menu): Open, Move, Add to/Remove from Home, Move to Apps/Streaming,
  Lock with code, Hide, App details, Uninstall.
- **Move**: choose Move once, then the arrows move the app until OK or Back.
- **App lock**: an arrow-key code (4–8 directions) for chosen apps and, optionally, Lumora settings.
- Themes: Amber, Copper, Rose, Moonlight. The theme colours the wallpaper grade, focus rings and icons.
- HD app icons from dashboard-icons, falling back to the installed icon.

## Install and update

### First install of a release build (one time)
Releases are signed with the repository's release key. A build signed with another key (any earlier
debug build) cannot be updated in place, so remove it once. This resets Lumora's settings.

1. Download `lumora-<version>-<code>.apk` from the latest
   [release](https://github.com/engziada/Lumora/releases/latest).
2. Install:

```powershell
$adb = '<path-to-adb>'
& $adb connect '<device-ip>:5555'
& $adb uninstall com.firestick.aura.launcher          # only if a debug build is installed
& $adb install '<path-to>\lumora-5.4.0-<code>.apk'
& $adb shell pm grant com.firestick.aura.launcher android.permission.WRITE_SECURE_SETTINGS
& $adb shell appops set com.firestick.aura.launcher REQUEST_INSTALL_PACKAGES allow
& $adb shell am start -n 'com.firestick.aura.launcher/.MainActivity'
```

The `appops` line allows Lumora to hand downloaded updates to the system installer. Without it,
Lumora shows a dialog that opens the "Install unknown apps" screen when Fire OS provides it.

### Updates
Settings › About › **Updates**: *Check now*, and the automatic check (Daily, Every 3 days, Weekly,
Off). The automatic check runs when Lumora's Home is on screen and the interval has passed.

Before the installer opens, Lumora verifies that the APK:
- comes from `https://github.com/engziada/Lumora/releases/download/…` (60 MB max);
- matches the SHA-256 and size published in `update.json`;
- has the same package name, a higher version code, and the same signing certificate as the
  installed Lumora.

Android's installer then asks for confirmation; nothing installs silently.

### Publishing (maintainer)
Every push to `main` runs CI; when tests and lint pass, it builds a release APK signed with the
key from the repository secrets `LUMORA_KEYSTORE_B64`, `LUMORA_KEYSTORE_PASSWORD`,
`LUMORA_KEY_ALIAS`, `LUMORA_KEY_PASSWORD`, and publishes release `build-<versionCode>` with the APK
and `update.json`. Version code = 1100 + CI run number, advancing beyond older installations
reporting build 1085. The feed reads its version code from the signed APK. Feature branch pushes
do not publish launcher updates. The 5 newest releases are kept. **Losing the
keystore means every device must uninstall once again**; keep an offline backup.

## One-time setup: quick actions and the Home button

Fire OS always opens Amazon's home on the Home button, ignores third-party home apps, and hides
accessibility switches for apps outside the Appstore. Lumora's accessibility service (*Lumora Quick
Actions*) handles Screenshot, Restart, returning to Lumora when the Fire TV home appears, the
hold-Menu assistant shortcut, and the Back/Home/Screenshot/Power-menu TV commands. To turn it on,
grant one permission once from a computer (Developer options › ADB debugging on):

```powershell
& $adb shell pm grant com.firestick.aura.launcher android.permission.WRITE_SECURE_SETTINGS
```

Lumora then enables its own service, adding it to any services already enabled, and re-enables it
if Fire OS turns it off. The service receives window events only from `com.amazon.tv.launcher`
and `com.amazon.firehomestarter`, looks only at the Menu key, and never reads screen content.

Limits:

- After Home, the Fire TV home can show briefly before Lumora returns.
- Restart opens the system power menu; a direct reboot needs a system signature.
- Locked apps are protected only when opened from Lumora, not from Fire TV menus or voice.
- Recent lists apps opened from Lumora only.
- The Alexa, Home and app-shortcut buttons are reserved by Fire OS and cannot be used by apps.
- Volume commands cannot change the TV's own volume when it is controlled over HDMI-CEC or IR.

## Network access

Lumora sends no analytics or telemetry. It contacts these hosts only for the listed features:

| Host | Feature | Default | Turn off in |
|------|---------|---------|-------------|
| `cdn.jsdelivr.net` (homarr-labs/dashboard-icons) | HD app icons and their name index | On | Settings › Appearance › App icons |
| `api.tvmaze.com`, `static.tvmaze.com` | Shows tab: schedules and posters | On | Settings › TV listings |
| `cdn.esahubble.org` | Space wallpapers (nebulae, galaxies) | On | Settings › Wallpaper |
| `images.unsplash.com` | Night sky and desert wallpapers (1280 x 720) | On | Settings › Wallpaper |
| `openrouter.ai` | Assistant questions and key test | Only with a key set | Settings › AI assistant › Remove key |
| `github.com` and GitHub's release-download host (`*.githubusercontent.com`) | Update check (`update.json`) and update download | Daily | Settings › About › Updates › Off |

Incoming: while the assistant's phone link (or *Enter key from phone*) is open, Lumora listens on
the TV's home-network address, port 8765–8775, for the paired phone only. It closes when that
screen is left or after 10 idle minutes.

Downloads are cached on the device (icons indefinitely, schedules 3 hours, posters 14 days,
wallpapers until the cache is cleared; only the last downloaded update APK is kept, replaced by
the next update download). With every feature off, Lumora makes no network requests.

### Home app preview experiment

Enable **Settings > Appearance > App preview demo** (off by default). On Home, focus YouTube,
Prime Video, or Netflix for 600 ms to reveal a passive title card. Three bundled samples rotate
every six seconds; OK opens the focused app through the existing lock and launch flow.
Leaving the strip, changing tabs, or leaving Lumora hides the panel. Locked apps have no preview.

This offline prototype uses sample titles, descriptions, and abstract vector artwork. It does not
access accounts, mirror an app screen, fetch live rankings, or establish regional availability.
No new permissions, keys, or network hosts are needed. Provider feeds remain future work.

Validation commands (JDK 17): `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`.
With a TV emulator connected, install the debug and androidTest APKs, then run
`adb shell am instrument -w com.firestick.aura.launcher.test/com.firestick.aura.launcher.PreviewInstrumentation`.
The instrumentation injects temporary service tiles, exercises focus/cancellation/rotation/opt-out,
and writes preview screenshots to the target app's external files directory. It restores its preview
and YouTube lock preferences afterward; it launches YouTube for the OK check when installed.
See [the preview plan](docs/APP_PREVIEW_PLAN.md).

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
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Bash:

```bash
export JAVA_HOME='<path-to-jdk-21>'
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```

Without `LUMORA_KEYSTORE_FILE` set, `assembleRelease` produces an unsigned release APK. Local debug
builds use the debug key and version code 9; they cannot update a release install (and vice versa).

Debug output: `build-out/app/outputs/apk/debug/app-debug.apk`

Protected Amazon packages can only be removed over ADB, Device Owner or root. Settings › ADB tools
lists the commands; Lumora never runs privileged package-manager commands itself.

## Project layout

```text
app/src/main/java/com/firestick/aura/launcher/
├── LumoraApp.java          Application; schedules the automatic update check
├── MainActivity.java       Home, Shows, Apps, Streaming, gear menu, move mode
├── AssistantActivity.java  Assistant chat panel, phone link, TV actions
├── QuickMenu.java          Quick menu
├── ShowDetails.java        Show details, watch-in-app, Appstore hand-off
├── SettingsActivity.java
├── adapter/                App tiles and cards, posters
├── assistant/              OpenRouter client, settings, TV actions and commands, phone link, QR encoder
├── manager/                Apps, app search, icons, shows, images, wallpapers, lock, preferences, device stats
├── model/
├── service/QuickActionService.java
├── ui/                     Glass overlays, glass menu, lock pad, text prompt, phone pairing, update flow
└── update/                 update.json parsing, schedule, download and verification
brand/                      Visual identity, palette, logo SVGs
docs/ASSISTANT.md           Assistant setup, phone link, TV control, privacy
tools/qr-reference/         Reference QR encoder used to derive test fixtures
```

## Continuous integration

`.github/workflows/android-build.yml` runs on pushes and pull requests to `main`: validates the
Gradle wrapper, runs the JUnit tests and `lintDebug`, assembles debug and release builds with
JDK 21 and uploads the debug APK (`lumora-debug`). On pushes to `main` it also signs the release
APK and publishes it as a GitHub Release (see *Publishing*).

## Credits

Listings and posters: [TVmaze](https://www.tvmaze.com) (CC BY-SA 4.0). App logos:
[dashboard-icons](https://github.com/homarr-labs/dashboard-icons) (Apache-2.0). Space wallpapers:
ESA/Hubble (CC BY 4.0). Night sky and desert wallpapers: Unsplash. Type: Poppins (SIL OFL 1.1).
Phone icon: Material Icons (Apache-2.0).

Made by Zakaa — support@zakaa-ai.net — https://zakaa-ai.net

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE). Bundled fonts, AndroidX and the Gradle wrapper retain their own
licenses; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
