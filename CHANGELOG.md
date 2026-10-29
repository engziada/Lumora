# Changelog

## 5.4.1 — Focus effect, assistant user guide

### Changed
- Focus ring is thinner: a 1.5 dp line on the card edge with a soft glow inside and outside, and no
  fill. The accent tint and the light sweep on the focused card are removed.

### Added
- **Focus effect** behind the focused app (Home cards and the Apps/Streaming tiles), in the theme
  colour, chosen in Settings › Appearance › Focus effect:
  - *Lightning* (default): random strikes with forks, quick flash and flicker, then fade.
  - *Arabic ornaments*: Arabic letters, the words نور and ضياء, and eight-point stars appear at
    random, drift and fade in and out continuously.
  - *Off*.
  Only the focused card animates (`ui/CardEffectView`, maths in `ui/CardEffects`).
- **User guide** in Settings › AI assistant: opening the assistant, typing or speaking from the
  phone, every instant command (English and Arabic), examples of free-form requests, limits, tips.
- JUnit: `CardEffectsTest`.

## 5.4.0 — TV control, self-update

### Added
- **TV control from the assistant** (Settings › AI assistant › TV control, default on):
  - Plain commands in English and Arabic ("pause", "volume up", "علي الصوت", "الحلقة التالية") run
    instantly on the TV, with no API key and no network (`LocalCommands`).
  - The model can request whitelisted actions with `[[do:…]]` directives: media keys, volume and
    mute, back/home/screenshot/power menu, settings pages, Lumora theme, search inside an installed
    app (`TvAction`, `TvController`). At most 4 per reply. Locked apps still ask for the code.
  - Volume reports "controlled by the TV" when HDMI-CEC/IR makes Android's volume fixed.
- **Self-update from GitHub Releases** (Settings › About › Updates):
  - *Check now*, plus an automatic check (Daily by default; Every 3 days, Weekly or Off).
  - The automatic check runs when Lumora's Home is shown and is due; it never installs silently.
  - Reads `releases/latest/download/update.json`; the APK must come from this repository's
    releases, match the published SHA-256 and size, have the same package name, a higher version
    code, and the same signing certificate as the installed app. Android's installer then asks
    for confirmation.
  - Needs "Install unknown apps" for Lumora once (dialog, or the ADB command in Settings › ADB tools).
- CI on every push to `main` builds a release APK signed with the repository key and publishes it
  as release `build-<versionCode>` with `update.json`; the 5 newest releases are kept.
- JUnit tests: `TvActionTest`, `LocalCommandsTest`, `UpdateInfoTest`, `UpdateScheduleTest`.

### Changed
- Version 5.4.0. Version code is `1000 + CI run number` for CI builds (local builds: 9), so every
  published build is newer than the last.
- The debug APK artifact is now named `lumora-debug`.
- CI runs for `main` only (branches were merged and deleted).

### Removed
- The Alexa skill (`alexa-skill/`) and its CI job: it could not control the TV and only duplicated
  what Alexa answers directly.

### Upgrade note
- Release builds are signed with a new key. **One-time**: uninstall the debug-signed Lumora and
  install the release APK; afterwards updates install in place. Settings and the OpenRouter key
  must be set again, and the ADB grants re-run.

## 5.3.0 — Hold Menu, phone voice input, Alexa skill (branch `ai-assistant`)

### Changed
- **Remote shortcut is now “hold ☰ Menu”** (600 ms) in any app via the quick-actions service, or
  inside Lumora when the service is off. A short press still works as Menu. The *Learn* button and
  the SEARCH / VOICE_ASSIST / ASSIST key list are removed: Fire OS never delivers the Alexa, Home or
  app-shortcut buttons to apps, so nothing could be learned. Old learn preferences are deleted on
  first start.
- App cards open their menu on Menu **release** (not press), so holding Menu does not also open it.
- The accessibility key filter now looks only at the Menu key.
- Version 5.3.0, version code 8.

### Added
- **Phone link**: the assistant's phone button shows a QR code; the phone page sends typed or
  dictated text (phone keyboard microphone, English or Arabic) to the TV over the home network.
  Settings › AI assistant › **Enter key from phone** uses the same link for the OpenRouter key.
  Security: LAN address only, 6-digit pairing code in the URL fragment, rotation after 5 wrong
  codes, Host check, size and rate limits, 10-minute idle stop, closes when the screen is left.
- Built-in QR encoder (`assistant/QrCode`, byte mode, ECC M, versions 1–6); no new dependency.
  `tools/qr-reference` holds the Python reference and OpenCV decode check behind the test fixtures.
- **Alexa skill** (`alexa-skill/`): private, development-mode Alexa-hosted skill — “Alexa, ask
  Lumora assistant …” — answered by OpenRouter, voice only. Key kept in the skill's private S3
  bucket. 17 Python unit tests; CI job *Alexa skill tests*. (Removed in 5.4.0.)
- JUnit tests: `LongPressTest`, `QrCodeTest`, `PhoneProtocolTest`.

## 5.2.0 — AI assistant, device info, card animation (branch `ai-assistant`)

### Added
- **Lumora Assistant** (`AssistantActivity`): a glass chat panel over any app, backed by OpenRouter
  (`openrouter/free` by default, custom model id optional). English and Arabic. Replies can suggest
  installed apps; *Open <app>* goes through Lumora so app locks and launch history apply.
  Mic button when the device has a speech recognizer.
- Two ways to open it: a floating button at the end of the bottom bar, and a remote button caught
  by the quick-actions service in any app (defaults: SEARCH, VOICE_ASSIST, ASSIST; *Learn* records
  the button the remote actually sends). Without quick actions the button works inside Lumora only.
- Settings › AI assistant: API key (masked, *Test key* uses `GET /api/v1/key` and spends no chat
  request), model, floating / remote toggles, learn / reset button, app-name sharing switch.
- Settings › Device info: live readings every second — model, system, CPU load (or clock when
  Fire OS hides `/proc/stat`), temperature, memory, storage, network (IP, Wi-Fi dBm and Mbps),
  display, uptime, Lumora memory.
- Home cards: focus animation on the card background — accent glow fades in and breathes, one
  light sweep crosses the card. (Replaced in 5.4.1.)
- Unit tests (JUnit 4) for reply parsing, key masking, request/response handling and device-stat
  maths; CI runs `testDebugUnitTest` before lint. See `docs/ASSISTANT.md`.

### Security and privacy
- The OpenRouter key is stored in its own preferences file, excluded from backup and device
  transfer (`backup_rules.xml`, `data_extraction_rules.xml`), shown masked, sent only to openrouter.ai.
- Sent to OpenRouter: the question, the last 12 turns and, if enabled, installed app names.
- The accessibility service now filters key events; it compares key codes against the assistant
  button and stores nothing. Navigation, media, volume and power keys are never captured.

### Changed
- Settings rail scrolls (10 sections).
- `ACCESS_WIFI_STATE` (normal permission) for Wi-Fi signal in Device info.
- Version 5.2.0, version code 7.

## 5.1.0 — TV feedback round

### Added
- Glass overlays: menus, quick menu, show details and lock pad sit on a blurred snapshot of the
  screen (Fire OS has no backdrop blur, so `ui/Glass` blurs a downscaled frame itself).
- Gear button beside the clock with a dropdown: Lumora settings / Fire TV settings. Settings left
  the bottom bar.
- App lock: arrow-key code (4–8 directions, salted SHA-256), per-app lock from the app menu,
  optional code for Lumora settings, 30 s lockout after 5 wrong tries.
- Move mode: choose Move once, then the arrows move the app until OK or Back.
- Apps and Streaming tabs are full grids; any app can be moved between them or added to Home.
- Shows: *Watch on <app>* opens the app on a search for the title; missing apps open their Amazon
  Appstore page; *Other apps* searches in any installed app that supports search.
- Wallpaper live catalog (on by default): ESA/Hubble nebulae and galaxies, Unsplash night sky and
  desert, graded beige/brown from the theme colour; categories selectable in Settings.
- Home button: when the stock Fire TV home appears, the quick-actions service brings Lumora back.
- Quick-actions setup without the hidden accessibility menu: one ADB grant of
  `WRITE_SECURE_SETTINGS`, after which Lumora enables its own service.
- About: version, build date, Zakaa logo, support e-mail and website.

### Changed
- Home keeps only the wide card strip, moved to the bottom, with the focused app as a hero title.
- Context menu is a compact glass list with icons.
- Quick menu: Recent, Clear memory, Screenshot, Restart, Wi-Fi (Sleep, Power off and Bluetooth removed).
- TV listings: local and global streaming services instead of every country's platforms; global
  services filtered to the country's languages (fixes Chinese titles for Egypt).
- Accent is now a theme that also grades the wallpaper.
- Launcher icon is full-bleed square (Fire OS letterboxes sideloaded icons; see README).
- Version 5.1.0, version code 6.

### Fixed
- Uninstalled or disabled apps stayed listed: rows are rebuilt on every resume and launch
  failures trigger a refresh.
- Entries guarded by a permission Lumora lacks (for example Amazon's Alexa settings) are no longer
  listed, which removes the "Permission Denial" toast.

## 5.0.0 — Lumora

### Added
- Lumora brand: warm espresso / amber palette, vector mark, wordmark, app icon and TV banner
  (`brand/` holds the identity docs and SVGs).
- Shows tab: today's TVmaze schedule for a selectable country plus streaming releases, poster grid,
  channel chips, details dialog with *Open in <app>*. Country and on/off in Settings › TV listings.
- Quick menu: Recent apps, Clear memory, Screenshot, Sleep, Restart, Power off, Wi-Fi, Bluetooth,
  with a first-use accessibility permission prompt and an ADB fallback.
- HD app icons from dashboard-icons via jsDelivr, resolved by package, name and alias index; falls
  back to the installed icon.
- Settings: six sections (Appearance, Wallpaper, Audio, TV listings, ADB tools, System), all text in
  `strings.xml`.

### Changed
- Tabs are Home / Shows / Apps / Streaming; the Live TV and Music tabs were dropped.
- HD icons now default to on (online wallpapers stay off).
- `QUERY_ALL_PACKAGES` replaced by `<queries>` for launcher intents.
- Back handling uses `OnBackPressedDispatcher`; `lint.xml` suppressions removed.
- Version 5.0.0, version code 5. Application id unchanged.

### Removed
- Z TV / Aura drawables (`ic_logo_z`, `banner_aura`, `ic_launcher_aura`).

### Correction
- The 4.0.0 entry below says `gradle.properties` pins JDK 21; it does not. Set `JAVA_HOME` to a JDK 21.

## 4.0.0 — 2026-10-27 (NOVA-style redesign)

### Added
- Poppins type family (OFL) applied through the theme.
- Hero greeting that follows the clock, with a status line.
- Featured strip of wide rounded tiles (pinned apps first).
- Bottom navigation: Home / Apps / Cinema / Settings, with row filtering per tab.
- Two-pane settings screen (section rail + panel) replacing the single scrolling column.
- HD logo pack: publisher logos fetched from Google's favicon service, disk-cached, with an
  in-app switch and fallback to the installed icon.
- Accent picker now Violet / Crimson / Cyan / Emerald, applied to focus rings and nav state.
- Icon plates so mismatched app artwork has a consistent optical size.

### Changed
- Complete visual rebuild: deep-navy surfaces, 24 dp glass panels, section count pills.
- App cards are smaller (108x112 dp) so two full rows fit above the navigation bar.
- Settings is a normal full-screen activity instead of a dialog-styled window.
- Version 4.0.0, version code 4.

### Fixed
- Concurrent icon requests for the same package no longer drop one of the callers (the row and
  the featured tile could end up with different icons).
- `gradle.properties` pins JDK 21: JDK 25 fails with "Unsupported class file major version 69".
## 3.0.0 — 2026-10-27

### Fixed
- **Move Left / Move Right did nothing after a restart.** The swap only modified the in-memory
  list; the order is now persisted (`pref_custom_order`) and applied when rows are rebuilt.
- **The accent theme was never applied.** `SettingsActivity` stored the colour but nothing read
  it; the favourites accent and the settings screen now use it (active theme is marked with a ✓).
- **Frozen backdrop when offline.** A failed wallpaper download replaced the background with a
  single static gradient every cycle; it now rotates through six local scenes.
- **Repeated full reloads.** `onResume` rebuilt the whole grid and re-decoded every icon; labels
  and drawables are now cached and rows are reused until preferences or packages change.
- **D-pad stutter from audio.** A new `AudioTrack` was allocated and torn down per focus event;
  two static tracks are now reused.
- **Double uninstall prompt** risk when the action dialog outlived a refresh: the dialog now
  closes before refreshing.

### Added
- `Wallpaper Source` setting: online 4K stills vs. offline procedural scenes.
- Hidden-apps manager: per-app restore list in Settings, with counts in the header.
- `Set Z TV as Default Home` helper (`Settings.ACTION_HOME_SETTINGS`).
- Live refresh on package install / removal / update via package broadcasts.
- Disk cache for downloaded wallpapers; downloads are downsampled to ~1280×1280.
- Launch counter and hidden-app counter on the settings screen.
- Empty-state handling: rows that would be empty are omitted.

### Verified on device (Fire TV Stick 4K Max, Fire OS 8.1.8.5)
- Cold start 2.9 s, no crashes; app launch, action dialog, favourites, focus ring.
- Order persistence proven across a `force-stop`: `pref_custom_order` written, row order kept.
- Memory: 95 MB PSS foreground cold start vs 188 MB for the stock launcher; swap 0.2 MB vs 26 MB.
- Native heap flat across wallpaper rotations after explicit bitmap recycling.

### Changed
- Version name `3.0.0`, version code `3`.
- Release build is minified and resource-shrunk.
- Gradle wrapper regenerated; `settings.gradle` redirects build output to `build-out/`.
- README rewritten for v3.

## 2.0.0 — earlier
- 3D floating icons, ambient glow, handwriting "Z" logo, rotating wallpapers, synthesised sounds,
  eight-action context menu, debloat controls, theme picker.

## 1.0.0 — earlier
- Initial launcher: rows, favourites, hidden apps, app actions, settings screen.
