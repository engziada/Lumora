# Lumora — v5.6.0

![Android CI](https://github.com/engziada/Lumora/actions/workflows/android-build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-E3A15A)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%2F%20Fire%20OS-8C5A34)

*Your world on a brighter screen.* A lightweight, ad-free home screen for Fire TV and Android TV.
Java application with no analytics SDK. The application id stays `com.firestick.aura.launcher`.

## Features

- **Home**: reorder or hide Favorites, Recently used, IPTV, Apps and Streaming rows in
  Settings → Appearance → Customize Home rows. Choose compact or comfortable tile containers.
- **Global search**: the header search button finds installed apps, public IPTV channels and settings.
- **IPTV**: a Live TV guide in the style of Pluto TV. A category rail (All channels, Favorites, the
  categories, Other, then the locked Adults) sits beside one row per channel: channel number and logo,
  the program on air with its progress and time left, then the next two programs. The focused
  channel's program fills the header. A muted preview (480p at most) starts once focus rests for a
  second. The guide lists only channels that are on air. Pluto TV and Samsung TV Plus channels count
  as on air; Lumora checks every other feed in the background, three at a time, and adds the channel
  once its feed answers. Results stay on the device (working feeds for 24 hours, failed ones for
  12 hours), so later visits check only new or expired channels. Favorites lists every favorite, and
  Refresh forgets the results and checks again. The program's rating (such as TV-14 or PG-13) shows
  beside its title in the header and in the player when Pluto TV or Samsung TV Plus publishes one.
  Country/name filters, favorites, an alphabetical country jump column and automatic alternate-feed
  fallback remain. Player controls include channel switching, audio, subtitles, picture size and the
  current/next guide of Pluto TV and Samsung TV Plus channels.
- **Subtitle style**: size, text colour, background, edge and position of subtitles in the IPTV and
  film players, or the Fire TV caption settings (the default). Open it from the player's Subtitles
  menu, press Menu during a film, or use Settings → IPTV → Subtitle style. Changes apply at once.
- **Recently watched**: successful live playback appears on Home with Continue live.
- **Movies**: films from 1990 on from Pluto TV's free On Demand library, in shelves: Continue
  watching, Recent releases, Trending now, New this month, Most popular, Action and adventure,
  Thrillers, Horror, Comedy, Science fiction, Crime, Romance, Drama and A24 films. Genre shelves put
  the newest first. Each film shows its year, length, genre and rating; most have English subtitles.
  Films play with Pluto TV's ads and need no account. Lumora remembers where you stopped and shows
  the time left. The **After dark · 18+** shelf (Pluto TV's late-night films with nudity or strong
  sexual content) keeps its films hidden until the lock key is entered. They hide again whenever
  Lumora leaves the screen, including when a film starts. NC-17 films appear only on that shelf.
  Where Pluto TV does not answer, the tab shows public-domain classics from the Internet Archive
  instead (Most watched, Film noir, Comedy, Horror, Science fiction, Westerns, Action and adventure,
  Romance, Drama and an After dark shelf of exploitation, burlesque and nudist films).
- **IPTV sources**: iptv-org, Free-TV direct streams, and the Pluto TV and Samsung TV Plus lineups
  that i.mjh.nz republishes with their schedules, merged into the same country/search/category views.
  Settings → Tools → IPTV sources turns Pluto TV (on by default), Samsung TV Plus (off by default)
  and Free-TV on or off, and adds or removes up to five HTTP(S) M3U playlist URLs.
  The Pluto and Samsung region is Automatic by default: it follows the IPTV country and uses the US
  lineup when a service has none for that country.
  **Free playlists** in the same menu switches on volunteer-maintained lists of free services: Tubi,
  The Roku Channel, Xumo Play and DistroTV, all for the United States. Their channels appear under
  that country and under All countries, and may play only there.
  Custom playlists are checked before saving; entries without country metadata appear under All countries.
  The locked Adults category collects groups named with an adult word (such as Adult, Adultos, XXX,
  NSFW, Porn, Erotic or 18+), channel names with an explicit word or an adult TV brand, and channels
  iptv-org's blocklist marks NSFW. It also lists the adult channels of every Pluto TV and Samsung TV
  Plus region (such as Pluto TV's softcore channels in the UK, Canada, Germany and Italy), whatever
  the selected region. iptv-org's own playlists leave NSFW channels out, so add a playlist that
  carries such channels for more.
  Stream alternatives retain supported User-Agent/Referer headers, including M3U EXTVLCOPT headers.
  Successful playback, startup time, buffering and failures help choose feeds on the next play.
  Observations expire and are bounded to 256 feeds. Source failures retain available catalogs and cached data.
  Catalog membership and HTTP Online indicators do not guarantee decoding or availability in every region.
- **Profiles and backup**: save/load appearance profiles and enable reduced motion in Appearance.
  Export/restore layout, favorites and launcher settings in Tools. Backups preserve the existing
  lock configuration. A saved key is required before export/restore when one is configured.
  Custom IPTV source settings and recent playback URLs are excluded from exported backups because URLs may contain access tokens.
  Devices without a document picker save backups under the app's external-files/backups directory.
  Restore can also select JSON backups received through Tools → Receive files from phone.
- **Apps** / **Streaming**: full grids. Any app can be moved between the two or added to Home.
- **Quick**: Recent, Clear memory, Screenshot, Restart, Wi-Fi.
- **Gear** (next to the clock): Lumora settings or Fire TV settings.
- **Assistant** (OpenRouter, free models): floating button or **hold ☰ Menu** in any app. Speak
  through your phone: scan the QR code and use the phone keyboard's microphone.
- **TV control**: "pause", "volume up", "next episode", "علي الصوت" run instantly (no key needed);
  the assistant can also open apps, search inside them and open settings pages.
  See [docs/ASSISTANT.md](docs/ASSISTANT.md).
- **Updates**: Lumora checks this repository's GitHub Releases (daily by default, configurable) and
  installs a verified new build after you confirm.
- **Device info**: live CPU, memory, storage, network and temperature readings.
- App menu (long-press or Menu): Open, Move, Add to/Remove from Home, Move to Apps/Streaming,
  Lock with code, Hide, App details, Uninstall.
- **Move**: choose Move once, then the arrows move the app until OK or Back.
- **App lock**: an arrow-key code (4–8 directions) for chosen apps and, optionally, Lumora settings.
  The same code opens the IPTV Adults category and the Movies After dark shelf.
- **Glass**: the navigation dock and the Settings panels are frosted glass over a blurred copy of
  the wallpaper. Fire OS has no backdrop blur, so Lumora blurs each wallpaper once at 1/8 scale.
  Dialogs sit on a blurred snapshot of the screen; pills, cards and guide cells are translucent tints.
  Settings → Appearance → Reduce transparency switches every glass surface to a solid fill.
- One amber accent colours the wallpaper grade, focus rings and icons.
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

When CI cannot publish a pushed `main` commit (for example while GitHub Actions is unavailable),
publish it from a Windows PC that holds the release keystore:

```powershell
pwsh -NoProfile -File tools/publish-release.ps1                       # dry run
pwsh -NoProfile -File tools/publish-release.ps1 -SkipChecks -Publish  # publish
```

The script mirrors the CI publish step. It takes the version code from the commit's failed CI run,
checks the keystore against the published certificate, runs the tests and lint (dry run), verifies
the signed APK, writes the same `update.json` and notes, keeps the 5 newest releases and checks the
live feed. It stops while the commit's CI run is in progress and does nothing if CI published the
commit. The keystore password comes from `LUMORA_KEYSTORE_PASSWORD`, a DPAPI-protected file (see
`Get-Help .\tools\publish-release.ps1 -Full`) or a masked prompt, and is never printed. Do not
re-run a CI run that was published locally: it would reuse the version code. Commits with
`[skip ci]` in the message run no CI and publish nothing.

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
| `iptv-org.github.io` | IPTV channel lists, categories and the NSFW blocklist | When the IPTV tab or search loads channels | — |
| `raw.githubusercontent.com` (Free-TV/IPTV) | Free-TV channel list | On | Settings › Tools › IPTV sources |
| `i.mjh.nz`, `raw.githubusercontent.com` (matthuisman/i.mjh.nz), `jmp2.uk` | Pluto TV and Samsung TV Plus lineups, schedules, Samsung TV Plus program ratings and stream redirects | Pluto TV on, Samsung TV Plus off | Settings › Tools › IPTV sources |
| `raw.githubusercontent.com` (BuddyChewChew/app-m3u-generator), `www.apsattv.com` | Free playlists: Tubi, The Roku Channel, Xumo Play, DistroTV | Off | Settings › Tools › IPTV sources › Free playlists |
| `boot.pluto.tv` and Pluto TV's service, image and stream hosts (`*.pluto.tv`) | Movies tab: film lists, posters and playback; ratings of the program on air on Pluto TV channels | When the Movies tab opens or a Pluto TV channel is focused or played | Ratings: Settings › Tools › IPTV sources › Pluto TV. Movies: — |
| `archive.org` and its download servers | Movies tab fallback: film lists, thumbnails and playback | When Pluto TV does not answer | — |
| `github.com` and GitHub's release-download host (`*.githubusercontent.com`) | Update check (`update.json`) and update download | Daily | Settings › About › Updates › Off |

Incoming: while the assistant's phone link (or *Enter key from phone*) is open, Lumora listens on
the TV's home-network address, port 8765–8775, for the paired phone only. It closes when that
screen is left or after 10 idle minutes. Settings › Tools adds two link modes that work the same
way: *Send a link from the phone* opens an http(s) URL the phone pastes in the TV's browser, and
*Send a file from the phone* streams a file (up to 512 MB) straight into the TV's Downloads
folder; APK files are saved there, never installed.

## Setup & health, Tools

Settings › **Setup & health** is a guided status screen: default home, quick actions, Home button
redirect, WRITE_SECURE_SETTINGS, update-install permission, AI key, automatic update check and
network connectivity each show a live status with a Fix action where one is possible in-app.
Repairs that require a computer stay referenced in Settings › ADB tools.

Settings › **Tools** holds the maintenance suite: a storage dashboard over the launcher's own
cache folders with a one-button cleanup, a network doctor that probes every service the launcher
talks to (updates, video previews, TV schedules, wallpapers, posters, the assistant), update
diagnostics (installed build, last check and the exact reason the last check failed), a stable/
beta update channel, and the phone link modes above. Pre-releases are published by CI whenever
the version name carries a beta/rc suffix.

Downloads are cached on the device (icons indefinitely, schedules 3 hours, posters 14 days,
Pluto TV and Samsung TV Plus lineups 1 hour, Samsung TV Plus ratings 6 hours, film lists 12 hours,
stream checks 24 hours (12 hours for feeds that failed),
wallpapers until the cache is cleared; only the last downloaded update APK is kept, replaced by
the next update download). With every feature off, Lumora makes no network requests.

### Home video preview feasibility build

The local debug app installs separately as **Lumora Preview Test** (`com.firestick.aura.launcher.localtest`).
Choose Settings > Appearance > Panel content: Particles, Videos, or Off. Particles is an offline,
transparent 3D sphere that reacts to focus movement, forms the focused app name, then bursts back
into a sphere. For Videos, focus Netflix, YouTube, or Prime Video on Home for 600 ms. A passive
video window appears in the middle-right above the app ribbon. Playback is muted and advances
after 15 seconds of playing; leaving the tile unloads the player.
OK still opens the focused application. Locked apps do not show previews.

The preview fetches recent public uploads from the official Netflix, Prime Video, and YouTube
channels through YouTube Atom feeds, with trailers/teasers preferred for Netflix and Prime.
There are no bundled fallback video IDs. Lists refresh on focus after a one-hour cache expires;
valid cached lists may be reused for up to one day when offline. Unavailable embedded videos are
skipped, stopping after a full failed cycle. Empty or failed feeds show an explicit message.

These are global recent-upload feeds, not trending charts or personalized recommendations.
The existing TV listings country preference scopes caching and selects Arabic player UI for
EG/AE/SA, English otherwise; it does not establish regional title availability. The official
YouTube channel highlights its own uploads rather than all videos across YouTube. Stremio,
CloudStream, and Tubi are outside this initial integration. App locks suppress previews.

Playback uses the official YouTube player and contacts YouTube/Google infrastructure including
youtube.com, ytimg.com, and googlevideo.com; provider playback data collection policies apply.
No API keys or streaming-account logins are required. Video fills the window without a launcher
header/footer. Loading and failure messages appear only when necessary. Player branding and
provider UI are controlled by YouTube. Debug builds remain separate from signed release installs.
The WebView player exists only in the Videos mode: it is created on the first videos focus and
fully destroyed when the panel leaves videos mode, so Particles and Off runs carry no WebView
memory cost.

Previews have a thin amber frame. Public video-dimension metadata is checked before playback:
only aspect ratios between 1.65 and 1.90 are accepted. Portrait, square, ultrawide, and unknown
dimensions are excluded. Decisions are cached for one day. This key-free check depends on
YouTube's public page structure and fails closed when dimensions cannot be read. It does not
extract playback URLs. Black bars encoded into an otherwise 16:9 video remain possible.

Build validation: `gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`.
The focus/lifecycle instrumentation remains available; actual playback and rotation require online
player telemetry and device observation rather than static title assertions.

**Movie posters:** TVmaze covers TV and streaming series only. Film posters would need TMDB, which
requires a free account and API key; it is not integrated. The Movies tab shows Pluto TV's own
posters, or the Internet Archive's thumbnails when it falls back to the archive.

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

Distribution options and publishing costs are tracked in [`distribute.md`](distribute.md).

## Project layout

```text
app/src/main/java/com/firestick/aura/launcher/
├── LumoraApp.java          Application; schedules the automatic update check
├── MainActivity.java       Home, IPTV guide, Movies, Apps, Streaming, gear menu, move mode
├── IptvPlayerActivity.java Full-screen IPTV player
├── MoviePlayerActivity.java Full-screen film player with resume
├── AssistantActivity.java  Assistant chat panel, phone link, TV actions
├── QuickMenu.java          Quick menu
├── ShowDetails.java        Show details, watch-in-app, Appstore hand-off
├── SettingsActivity.java
├── adapter/                App tiles and cards, posters, guide rows and rail, film cards
├── assistant/              OpenRouter client, settings, TV actions and commands, phone link, QR encoder
├── manager/                Apps, app search, icons, shows, IPTV and FAST lineups, free playlists,
│                           stream checks, program ratings, films, Pluto TV session, images,
│                           wallpapers, lock, preferences, device stats
├── model/
├── service/QuickActionService.java
├── ui/                     Frosted glass, glass overlays and menu, guide preview, Movies tab, rating
│                           badge, subtitle style, lock pad, text prompt, phone pairing, update flow
└── update/                 update.json parsing, schedule, download and verification
brand/                      Visual identity, palette, logo SVGs
docs/ASSISTANT.md           Assistant setup, phone link, TV control, privacy
tools/publish-release.ps1   Local release publishing when CI cannot publish
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
Phone icon: Material Icons (Apache-2.0). Channel lists: [iptv-org](https://github.com/iptv-org/iptv),
[Free-TV](https://github.com/Free-TV/IPTV), [i.mjh.nz](https://github.com/matthuisman/i.mjh.nz)
(Pluto TV and Samsung TV Plus), [app-m3u-generator](https://github.com/BuddyChewChew/app-m3u-generator)
and [apsattv.com](https://www.apsattv.com) (free playlists). Films: [Pluto TV](https://pluto.tv)
On Demand (free with ads; Lumora is not affiliated with Pluto TV) and the
[Internet Archive](https://archive.org), public domain as marked by their uploaders.

Made by Zakaa — support@zakaa-ai.net — https://zakaa-ai.net

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

MIT — see [LICENSE](LICENSE). Bundled fonts, AndroidX and the Gradle wrapper retain their own
licenses; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).

## Local checkpoint — 2026-11-08

Locked app names now form with an orbiting particle lock, app operations require the key, and
Quick Menu uses compact rows beside Settings with a Play/Pause remote shortcut.
See [implementation and validation notes](docs/LOCKED_APPS_QUICK_MENU_2026-11-08.md).

## Local checkpoint — 2026-11-09

Refined the lock to a smaller title-corner mark with two bounces, matched the Quick icon to
Settings, and added animated focus-driven detail panes. See [refinement notes](docs/QUICK_MENU_REFINEMENT_2026-11-09.md).

## Local checkpoint — Recent, Wi-Fi and navigation

Added Recent app icons, optional Wi-Fi-name permission access, an explicit bounded internet
download test, and separate D-pad navigation for menu/content panes. See
[validation notes](docs/RECENT_WIFI_NAVIGATION_2026-11-09.md).

## Enhancement verification

Run `gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`.
On an Android TV emulator, install the two debug APKs and run
`adb shell am instrument -w -e enhancements true com.firestick.aura.launcher.localtest.test/com.firestick.aura.launcher.PreviewInstrumentation`.
The device suite checks the filter focus path, the existing Adults key, online progress,
real local-video fallback, successful watch history, player controls, search and Settings entry points.
Online indicators measure endpoint reachability. Stream decoding and guide coverage depend on the provider.

Program schedules come from the Pluto TV and Samsung TV Plus lineups that i.mjh.nz republishes.
Other channels have no schedule. Program ratings come from Samsung TV Plus's XMLTV guide on
i.mjh.nz and from Pluto TV's own guide, which answers only for the region Pluto TV serves the
device; Pluto TV channels of other regions show no rating.

During full-screen IPTV playback, remote Up/Down switches next/previous channels. OK opens player controls; Back hides them before exiting playback. Channel Up/Down buttons remain available while controls are visible.
