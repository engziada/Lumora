# Approved launcher enhancement plan

## Combined IPTV catalog (5.5.1)
- [x] Merge Free-TV/IPTV with iptv-org by channel ID, exact URL, or unambiguous country/name; preserve favorite aliases.
- [x] Enrich channels with iptv-org streams API alternatives and required HTTP headers.
- [x] Add Settings > Tools > IPTV sources: curated-source toggle and validated custom M3U URLs; private source settings stay out of exported backups.
- [x] Persist bounded device-local playback health, rank feeds by success/startup/buffering, and retain automatic fallback.
- [x] Verify merging, country/category mapping, partial-source failures, header persistence, ranking and TV flows; build and lint.

Validation: 113 JVM tests and 75 TV emulator assertions, debug/instrumentation builds, lint and an optimized release build. Live catalog snapshot on 2026-11-11: 10,331 primary channels → 11,274 merged channels, with 29,692 retained stream URLs after enrichment. These are catalog counts, not verified online channel counts. GitHub CI verifies the pushed release.

- [x] Group IPTV feeds by stable channel identity, preserve existing favorites, try alternate feeds on playback failure.
- [x] Add saved recent channels, Home IPTV row and a continue-live shortcut.
- [x] Check online state with three workers, progress, refresh, cancellation and bounded queues.
- [x] Add remote channel switching, audio/subtitle selection and picture sizing.
- [x] Resolve available XMLTV sources from the public guide catalog and show current/upcoming programs on demand.
- [x] Keep Adults locked and place it last in the category menu.
- [x] Add global search across installed apps, public channels and settings.
- [x] Add configurable Home sections, ordering and tile density.
- [x] Add SAF backup/restore with validation and existing lock verification.
- [x] Add saved appearance profiles and reduced motion.
- [x] Improve app-list caching, image memory and focus restoration.
- [x] Enhance update status and retain existing verified download/install flow.
- [x] Expose app storage and notification shortcuts through app menus where supported.
- [x] Validate parsing/migration/backup with JVM tests, compile/lint/release builds and TV emulator navigation.

- [x] Add country letter index and Up/Down channel switching while preserving player control navigation.

Validation: 103 JVM tests, Android lint, debug/instrumentation APK builds, 43 enhancement device assertions, 59 existing navigation assertions. A 720p pass covered the initial 37 enhancement checks. Release publication is verified through CI.
