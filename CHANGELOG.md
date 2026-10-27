# Changelog

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
- Disk cache for downloaded wallpapers; downloads are downsampled to ~1280×720.
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
