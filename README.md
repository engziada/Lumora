# Z TV Launcher — v4.0.0 (NOVA-style redesign)

![Android CI](https://github.com/engziada/Lumora/actions/workflows/android-build.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-8B7CFF)
![Platform](https://img.shields.io/badge/platform-Android%20TV%20%2F%20Fire%20OS-00A8E1)

A lightweight, ad-free launcher for the Fire TV Stick 4K / 4K Max. Java only, no analytics SDK,
no web views. This release replaces the old visual system with the NOVA-style design:
greeting hero, featured tiles, plated icons, bottom navigation and a two-pane settings screen.

| | Stock Fire TV launcher | Z TV v4 |
| :--- | :--- | :--- |
| Total PSS, foreground | 188 MB | 95 MB |
| Swap in use | 26 MB | 0.2 MB |
| Cold start | heavy | ~2.9 s |
| Ads / sponsored rows | yes | none |
| Telemetry | Prism / Unagi | none shipped |

Measured with `dumpsys meminfo` on an AFTKM (Fire OS 8.1.8.5, Android 11, 1080p).

---

## The redesign

**Home**
- Poppins type family, deep-navy surface (`#05070E`), 24 dp glass panels and violet accent.
- `GOOD MORNING / AFTERNOON / EVENING` greeting that reflects the clock and a one-line status.
- **Featured strip**: wide rounded tiles with the icon, name and a meta line — pinned favourites
  first, otherwise the start of your library.
- **Rows**: section title + count pill + evenly plated app icons (consistent optical size).
- **Bottom navigation**: Home / Apps / Cinema / Settings. Apps and Cinema filter the rows to the
  library or to streaming apps; Settings opens the control centre.

**Settings (two-pane)**
- Section rail on the left (Appearance, Wallpaper, Audio, Debloat, System), panel on the right.
- Accent picker (Violet, Crimson, Cyan, Emerald) with a live "active accent" line; the choice
  drives focus rings, the selected tab and the favourites row.
- **Icon style switch**: *HD Logos (online)* or *Installed app icons*.
- Wallpaper interval + source, audio toggle, debloat actions, hidden-apps manager, default-home
  helper.

**Icons**
- Every icon sits on a rounded glass plate so unrelated artwork lines up.
- HD logos are fetched per package from Google's public favicon service (256 px, downsampled to
  128 px), cached in `cache/logos`, and fall back to the installed icon when offline or when the
  package is not in the mapping. Nothing is bundled in the APK, so no trademarked artwork is
  redistributed; the trademark stays with its owner and the request is a normal image fetch.

**Type**
- Poppins Regular / Medium / SemiBold (SIL Open Font License, see `FONT-LICENSE-OFL.txt`).

---

## What is not in this build

The reference image shows things a launcher alone cannot produce, and I did not fake them:

| Reference element | Status |
| :--- | :--- |
| Brand logos in the icon grid (Netflix N, Disney+, Plex…) | Reached via the online logo pack, not bundled (trademark). |
| "Dune / Fallout / The Last of Us" poster shelf under Movies & TV | Needs a metadata provider (TMDB) plus poster licensing. Not implemented. |
| Live TV, Music tabs with channel data | Needs TV input / media session access. The nav uses Home / Apps / Cinema / Settings instead. |
| "Continue watching" progress | Fire OS does not expose watch history to third-party launchers. |
| Exact wordmark and spacing | NOVA's typeface is not specified; Poppins is the closest open substitute. |
| Screenshot / Clear memory / Sleep quick tools | Requires system permissions; only Fire TV Settings deep-links are implemented. |

---

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

Making it the permanent HOME app is blocked on Fire OS 8.1.8.5: the Amazon launcher,
`firehomestarter` and parental controls are **protected packages** (`Cannot disable a protected
package`), and `cmd package set-home-activity` reports success while HOME still resolves to
Amazon. Root or Device Owner is required; the in-app debloat buttons have the same limitation.

---

## Project layout

```
app/src/main/java/com/firestick/aura/launcher/
├── MainActivity.java          # hero header, featured strip, rows, bottom nav, app actions
├── SettingsActivity.java      # two-pane settings
├── adapter/
│   ├── AppRowAdapter.java     # plated icon cards + focus ring + HD logo swap
│   ├── FeaturedRowAdapter.java# hero tiles
│   └── CategoryRowAdapter.java# section header + horizontal row
├── manager/
│   ├── AppRepository.java     # package query, icon/label cache, ordering
│   ├── PreferenceManager.java # favourites, hidden, accent, wallpaper, HD icons
│   ├── IconService.java       # online logo pack with disk cache
│   ├── WallpaperManager.java  # offline scenes + online stills, bitmap recycling
│   ├── DebloatManager.java
│   └── SoundManager.java
├── model/                     # AppModel, AppCategory
└── receiver/BootReceiver.java
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md).
## Continuous integration

`.github/workflows/android-build.yml` builds the debug APK on every push to `main` and on pull
requests, then uploads it as the `ztv-launcher-debug` artifact (30 days) with its SHA-256 in the
job summary. The job runs on Ubuntu with JDK 21 and needs no signing secrets, because it is a
debug build.

Locally, `gradlew.bat` uses whichever JDK `JAVA_HOME` points at. If that is JDK 25, pin JDK 21 in
your user-level Gradle config — `%USERPROFILE%\.gradle\gradle.properties`:

```properties
org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot
```

Machine-specific paths are deliberately not committed, so CI and other machines are unaffected.

## License

MIT — see [LICENSE](LICENSE). Bundled fonts, AndroidX and the Gradle wrapper keep their own
licenses; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). The Poppins fonts are under the
SIL Open Font License 1.1 ([FONT-LICENSE-OFL.txt](FONT-LICENSE-OFL.txt)).