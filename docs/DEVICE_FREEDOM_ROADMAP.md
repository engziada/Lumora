# Lumora: device freedom and user experience roadmap

Planning date: 30 October 2026. Status: proposal; no implementation or device changes authorized by this document.

## 1. Product direction

Make Lumora the place where users choose their home screen, applications, content sources, privacy settings, and maintenance actions. Keep the everyday launcher fast and simple; put deeper controls in an optional Device Control area.

The product promise should be: **Your TV, your home screen, your apps, your choices—with clear limits and a way back.**

Do not require a Lumora account, subscription, cloud connection, or Amazon account integration for core launcher and maintenance functions. Offer portable settings, user-owned repositories, local phone control, and offline operation. Treat interoperability and recoverability as product features.

## 2. Verified project baseline

Reviewed the local working tree, including README, Gradle configuration, manifest, DebloatManager, QuickMenu, QuickActionService, and UpdateManager. No physical-device behavior was tested for this plan.

- Build configuration: Java, version 5.4.1, minimum API 26, target/compile API 34. README still identifies 5.4.0.
- Already present: ad-free home, themes and wallpapers, app organization/hiding/launcher-local locking, shows listings, device readings, assistant, phone text bridge, Menu hold shortcut, Home redirect, and verified self-update.
- DebloatManager currently supplies command strings and launches normal uninstall UI. It does not execute privileged package changes.
- Its telemetry package list is a starting point for investigation, not a validated cross-device removal policy.
- Home redirect uses accessibility events from Amazon home components. It is not a guaranteed default-launcher replacement.
- Clear memory calls killBackgroundProcesses; it is not a storage cleaner.
- Self-update already validates package identity, signing certificate, size, hash, version, and source. This is a useful basis for app installation, but its strict Lumora-only trust boundary must remain separate.

## 3. Compatibility and privilege model

Show each action with one of these capability states: Ready; Setup required; Companion required; Unsupported; Untested on this firmware. Explain the missing requirement and a concrete next step.

| Level | Mechanism | Intended uses |
|---|---|---|
| Basic | Normal application APIs and user-selected file access | Launcher, own cache cleanup, downloads, package inspection, user-confirmed installation, settings shortcuts |
| Granted access | Explicit special access or accessibility authorization | Home redirect, supported shortcuts, usage information where available |
| External ADB | User-authorized computer or Android companion connected to the TV | Supported package enable/disable, deeper diagnostics, selected settings and app resets |
| Experimental local bridge | On-device ADB or another privileged service, only after a device-specific proof | Optional convenience, never a required architecture assumption |
| Root | Separate future adapter on already-rooted devices | Advanced functions; outside the initial product |

WRITE_SECURE_SETTINGS is not a grant of general shell access. An ADB connection also does not imply every protected package can be changed. Probe specific operations and report rejected operations accurately.

Support Android-based Fire OS and Android TV first. The current minimum API 26 excludes Fire OS 6/API 25 and Fire OS 5/API 22. Decide explicitly whether lowering the minimum is worth the compatibility work. Vega is a separate platform; the existing Android APK is not a Vega port. Use measured API level, model, build fingerprint, and capability probes rather than marketing names alone. [Amazon platform table](https://developer.amazon.com/docs/fire-tv/fire-os-overview.html).

## 4. Feature pillar: ad and tracker controls

### A. Clean home experience — first release

- Retain zero sponsored placements, autoplay previews, or paid ranking in Lumora.
- Let users remove every content row, including Shows, and start directly in Favorites, Apps, or a chosen app where startup behavior permits it.
- Offer local-only wallpapers and icons and a single Offline mode switch.
- Show Home redirect health, the reason it stopped, and a guided repair flow.
- Offer a temporary Return to Amazon Home action for settings and troubleshooting.

### B. DNS filtering — after compatibility research

- Initially provide guided setup for a user-chosen router/DNS filter and a connectivity test.
- Evaluate an optional on-device VPN-based DNS filter, with custom lists, allowlists, pause timer, and local query counters.
- Support named profiles such as Balanced, Privacy, and Custom. Explain exactly which rules differ.
- Provide a streaming troubleshooting action that temporarily pauses filtering and compares connectivity.
- Keep logs disabled by default or short-lived; allow immediate deletion.
- Distinguish DNS reachability from proof that a particular app is using the configured resolver.

DNS filtering can affect requests to distinct advertising/tracking domains. It cannot reliably distinguish ads from wanted content delivered through the same endpoint; hardcoded addresses or app-managed encrypted DNS can bypass a simple DNS design. Do not advertise universal removal of video ads.

A VPN-based filter occupies Android's single active VPN service slot for that user/profile. Offer router-level filtering for users who need another VPN, and never silently replace that VPN. Fire OS support requires device testing. [Android VPN architecture](https://developer.android.com/develop/connectivity/vpn).

### C. System privacy controls — companion phase

- Expose relevant Amazon privacy/settings screens and explain their effects.
- Offer independently verified telemetry-service disable recipes for exact supported builds.
- Record changed settings and package states so users can restore them.
- Keep firmware-update control separate from ad filtering; investigate it only as an advanced compatibility-dependent feature. Explain loss of security updates and avoid broad domain lists that also break login or streaming.

Acceptance: clean launcher works offline; filtering has a visible status and pause action; VPN conflict is handled; selected streaming/login test cases still work; no claim that all ads or telemetry are removed.

## 5. Feature pillar: reversible debloater

Build a searchable inventory showing package name, display name, installed version, system/user status, enabled state, observed storage information, and available actions. Show unavailable data as unknown.

Actions must be distinct: hide from Lumora; open app settings; uninstall a user app; disable a supported package; restore its previous state; advanced uninstall-for-current-user where supported. Explain that disabling does not reclaim the system APK's space and user-level removal is not physical removal from a read-only system image.

Profiles:

- Minimal: hide unwanted tiles and remove user-selected installed apps.
- Balanced: apply only tested disable recipes with low dependency impact on the matching device build.
- Custom: review each package and its impact individually.
- Experimental: quarantined from ordinary presets, with explicit unsupported/untested status.

Every recipe contains model/API/build applicability, package identity, evidence/date, affected features, dependencies, preconditions, action, verification, and restoration procedure. Prefer disabling to removal when it achieves the user's objective.

Workflow: scan -> select -> preview consequences -> capture existing state -> apply serially -> verify actual state -> show result and available restore actions. Stop a batch after unexpected critical failures. Export the operation record to the companion before changing launcher-related components.

Protect setup, settings, installer, networking, input, DRM/playback, and recovery dependencies. Do not put stock-launcher disabling in a default preset. Retain external recovery when the launcher cannot start; an in-app Undo button alone is insufficient. Restore the exact prior enabled state, not blindly enable everything.

Snapshots contain package/settings state, not app-private data backups. Restoring a removed app's availability may depend on its system image or a trusted installer; it does not restore deleted logins or user data.

Acceptance: each supported recipe survives reboot and a disable/restore cycle on its declared device; unrelated apps remain untouched; partial failure and disconnected-companion cases produce useful results.

## 6. Feature pillar: storage cleaner and app health

### Storage dashboard

- Total/free space, Lumora-owned cache, retained installers, accessible downloads, and large user files.
- Per-app sizes only where the platform exposes them; show measurement time and unavailable categories.
- Sort by size and last use where usage access exists. Without it, label activity as Opened from Lumora rather than device-wide use.
- Track local storage trends with bounded history and warn before a download exceeds available space.

### Cleanup categories

| Category | Default behavior |
|---|---|
| Lumora image/poster/temp caches | Preview and clear; enforce configurable size/age limits |
| Successfully installed APK downloads | Offer automatic deletion after verified install success |
| Failed downloads and old partial files | Show age and size; remove after a bounded retention period |
| Duplicate APKs/files in accessible locations | Match hashes; preserve selected copies; never infer duplicates from names alone |
| User downloads, screenshots, media | User selection; preview; optional trash with explicit quota |
| Other apps' cache | Settings handoff first; companion method only when verified on that build |
| Other apps' data | Separate Reset app workflow naming lost sessions/settings/downloads |
| Suspected orphan folders | Review-only suggestions; no automatic deletion based on folder names |

Do not treat pm clear as cache-only cleaning. Never select private-data reset in a one-click clean action. Do not promise undo after data deletion. A trash folder consumes storage until emptied; report that honestly.

Normal apps cannot read and delete arbitrary private data belonging to other apps. Even broad shared-storage access does not remove that boundary. Use system file selection where available; provide phone/companion transfer when Fire TV's file picker is missing. [Android app storage](https://developer.android.com/training/data-storage/app-specific), [all-files access limits](https://developer.android.com/training/data-storage/manage-all-files).

### App health

Replace the prominent RAM-boost framing with useful actions: identify low storage, open app settings, restart a selected app through a supported method, and explain observed symptoms. Android 14 restricts killBackgroundProcesses to the caller's own processes; repeated blanket process killing is not a sound performance strategy. [Android behavior changes](https://developer.android.com/about/versions/14/behavior-changes-all).

Acceptance: scan is cancellable; cleanup does not run on the UI thread; protected paths and symlinks cannot escape the chosen scope; measured deleted bytes and observed free-space change are reported separately; interrupted operations resume or fail cleanly.

## 7. Feature pillar: App Hub and Unlinked-style libraries

Deliver an open repository client: search publisher releases and repositories the user chooses, install APKs, manage updates, and share collections through URLs, QR codes, and short codes.

### Discovery

- Start with direct HTTPS APK URLs, local uploads, and a documented JSON repository format.
- Add GitHub release sources and adapters for suitable open-source repositories after format and redistribution review.
- Search enabled repositories, filter by TV support, Android/API compatibility, ABI, language, category, size, and update channel.
- Support legitimate community forks and modified builds as separately identified publishers/packages. Show source, signing fingerprint, permissions, release notes, and upstream relationship where provided.
- Let users add their own sources; never label arbitrary repositories as verified merely because they use HTTPS.
- Treat Unlinked-style codes as a proposed Lumora library mechanism. Actual interoperability requires documented format/API compatibility and separate testing.

### Trust and installation

- Display publisher identity and source status: known publisher, user-trusted source, or unverified metadata.
- Verify download size and checksum, parse package metadata, inspect signing identity, check API/ABI compatibility, and confirm adequate free storage.
- A hash from the same compromised server is not independent proof of authenticity. Support signed repository metadata, key pinning, expiry/replay protection, revocation, and deliberate publisher-key changes.
- Reject unexpected signer changes on updates. Explain that a fork with the same package ID but a different signer normally cannot update the original in place.
- Queue downloads; support cancel/retry and installation status. The first release supports single APKs; split APK sets follow with completeness and signature checks for every split.
- Use Android's package installer and its confirmation flow. Do not claim silent ordinary-app installation. [PackageInstaller API](https://developer.android.com/reference/android/content/pm/PackageInstaller).
- Show exact errors: unsupported architecture/API, conflicting signer, missing split, insufficient storage, permission missing, blocked by OS, or user cancellation.

### Libraries and updates

- Collections such as Living room, Local media, Accessibility, and Developer tools.
- Export a collection as a manifest containing references and preferences, without redistributing APKs or secrets by default.
- Let users select an update channel, pin a version, ignore an update, and approve updates per app.
- Cache a previous installer only if requested and storage permits. Downgrades can be refused or require reinstall/data loss; previous APK retention is not guaranteed rollback.
- Short codes resolve to a displayed repository identity and URL before trust is established. Prefer self-hostable resolution; long URLs/QR codes remain usable if the code service disappears.

For modified packages, provenance and clear identity are more useful than anonymous web scraping. Default discovery should emphasize publisher releases and maintained forks; arbitrary internet results must not be converted directly into trusted installation suggestions.

Acceptance: wrong signer, corrupt hash, expired metadata, malformed repository, unsafe redirect, HTML masquerading as APK, incomplete split set, and interrupted download all fail safely; successful installation is verified before deleting its installer.

## 8. Feature pillar: phone and desktop companion

Extend the current PhoneBridge with explicit capabilities rather than exposing a generic remote shell.

- Browser companion: keyboard/dictation input, remote controls, APK/file upload, URL sending, arranging home tiles, repository editing, and backup download.
- Desktop companion: authorized ADB setup, supported package operations, diagnostic export, recovery, and multiple TVs.
- Consider an Android companion later for direct TV ADB management after transport/authorization testing. A normal phone browser does not itself supply Android shell privileges.
- Pair explicitly with an on-screen code; use expiring sessions, device revocation, rate/size limits, request authentication, origin checks, and replay protection.
- Harden transport before expanding into privileged controls. Sensitive tokens/ADB keys must not be logged or included in ordinary backups. Keep ADB keys local to the authorized companion.
- LAN-only by default. Never expose an unauthenticated remote administration endpoint to the internet.
- File transfers should stream to bounded temporary storage and verify completion before installation.

Acceptance: unpaired clients cannot control the TV; expired/revoked sessions fail; interrupted uploads are bounded; the PC can restore supported changes while Lumora is unavailable. [Amazon ADB connection flow](https://developer.amazon.com/docs/fire-tv/connecting-adb-to-device.html).

## 9. Everyday experience and creative additions

| Feature | User benefit | Scope and dependency |
|---|---|---|
| Layout editor | Folders, named rows, custom tile art, density, quick launch | Basic; first expansion |
| Household profiles | Different favorites, appearance, language, and restrictions | Lumora-local profiles; no claim of OS account isolation |
| Guest mode | Temporary home configuration without changing the owner's layout | Does not erase third-party app sessions |
| Accessible mode | Large text, reduced motion, high contrast, focus announcements | D-pad and screen-reader testing required |
| Arabic and RTL | Full Arabic UI, layout mirroring, mixed-direction text handling | Current manifest disables RTL; audit layouts and navigation |
| Unified search | Apps, settings, own files, enabled repositories, supported content links | Query installed integrations; no screen scraping required |
| Watchlist | User-owned list and preferred playback providers | Cross-app resume only where a supported API/deep link exists |
| Local media home | SMB/DLNA browsing and launch in a chosen player | Optional module; credentials protected; no DRM bypass |
| Personal live-TV row | User-supplied playlists/EPG or tuner integration | Validate formats; no bundled subscriptions/content claims |
| Screensaver studio | Local albums, clock, weather option, subdued animation | True system screensaver registration is platform-dependent |
| Input actions | Remap keys actually delivered to the app/service | Reserved Alexa/Home/vendor keys may remain inaccessible |
| Movie mode | Apply theme, dim launcher UI, open selected player | Does not imply control over TV hardware brightness or IR volume |
| Local automation | Time/idle-based own-cache cleanup, profile selection, reminders | Respect playback and OS scheduling limits |
| Network doctor | DNS/latency checks, throughput on request, connection history | No continuous speed tests or misleading bandwidth claims |
| Migration suitcase | Transfer layouts, repositories, watchlists, and app references | Schema versioning; optional encrypted secrets; no private app data promises |
| Device change report | Explain which capabilities changed after an OS update | Re-probe; no silent reapplication of invasive changes |
| Recovery screen | Minimal home mode after repeated startup crashes | Independent companion recovery for failures before UI starts |

Assistant enhancement: let users ask “What can I safely delete?” or “Why did this install fail?” The assistant explains measured facts and prepares a typed action preview. The deterministic executor validates every action; repository descriptions and model output cannot authorize shell commands, installations, or deletion.

## 10. Proposed navigation

Keep Home, Apps, Streaming, and optional Shows. Add a removable Tools tile opening Device Control with six destinations: Storage, Apps & debloat, Privacy, App Hub, Connections, and Backup & recovery.

Each destination begins with a useful summary and a primary action. Advanced detail is one level deeper. Do not fill the home screen with warnings or a fabricated device health score. Use concrete statements such as “240 MB of retained installers” or “Home redirect needs setup.”

First-run flow: choose layout -> detect capabilities -> offer optional Home setup -> finish. Invite cleanup, repositories, and companion setup when users open those features; do not impose all permissions at onboarding.

## 11. Implementation architecture

Keep Java and the existing UI stack initially. Avoid an unrelated framework rewrite.

- capability/: DeviceProfile and CapabilityRegistry with states, evidence, and last probe time.
- operations/: typed ActionPlan, preconditions, preview, executor, result verifier, operation journal, and restoration actions.
- privileged/: PrivilegedBridge interface, companion adapter, and optional future device-specific bridges. Commands originate from allowlisted operation templates, never remote recipe shell text.
- packages/: PackageInventory, dependency/risk metadata, RecipeRepository, DebloatPlanner.
- storage/: StorageScanner, CleanupPlanner, bounded cache policy, deletion executor.
- catalog/: RepositoryClient, metadata signature verifier, source registry, search, update policy.
- install/: DownloadQueue, APK verifier, installer session adapter, result handling.
- companion/: pairing/session/transport services, building on PhoneBridge while separating assistant input from administration permissions.
- backup/: versioned settings export/import, migration, optional encrypted secrets.
- privacy/: diagnostics and optional filtering module after feasibility validation.

Use SQLite for journals, download jobs, repositories, and capability evidence; keep small preferences in the existing preference layer. Each operation stores its target, pre-state, intended post-state, timestamps, result, and recovery metadata. Never retry destructive operations blindly after a disconnect.

Retain the existing self-updater's Lumora-only source policy. Share low-level download/hash utilities with App Hub, not third-party trust configuration. Build large features as optional modules/components so disabled features impose minimal startup, memory, and network cost.

## 12. Delivery sequence and estimates

Estimates are planning ranges in engineer-weeks for one experienced Android engineer with part-time QA and access to representative physical devices. They are not delivery promises; revise after Phase 0. Appstore review, hardware acquisition, and long-term recipe maintenance are excluded.

| Phase | Deliverable | Dependencies | Estimate | Exit gate |
|---|---|---|---|---|
| 0 | Capability matrix, device inventory, benchmarks, privilege/installer experiments | Test devices and recovery access | 1–2 weeks | Supported methods demonstrated; unsupported paths documented |
| 1 | Device Control shell, own-storage cleaner, settings backup, layout improvements | Phase 0 basic capability model | 3–4 weeks | Useful with no ADB; no data-reset paths in clean action |
| 2 | Desktop ADB companion, operation journal, small tested debloat catalog, recovery | Phase 0 privilege proof; Phase 1 UI | 4–6 weeks | Disable/restore, reboot, and interrupted-operation validation |
| 3 | App Hub: URLs, uploads, signed repositories, search, single APK install/update | Capability model; operation infrastructure | 4–6 weeks | Trust and install failure matrix passes |
| 4 | Router DNS guidance, privacy diagnostics, optional VPN-filter prototype | Physical networking/VPN tests | 3–5 weeks | Conflict and streaming regression tests pass before release |
| 5 | Profiles, full Arabic/RTL, advanced companion, local media and selected automation | Stable core; pick by user feedback | 4–8 weeks | Accessibility, low-memory, and playback acceptance |

Core ownership release through Phase 3: approximately 12–18 engineer-weeks. Full listed sequence: approximately 19–31 engineer-weeks. Native VPN packet handling, broad split-package support, root features, and Vega porting can expand this substantially.

If prioritizing fast visible value, Phase 1 can ship independently. If sideloading is the dominant demand, move the basic single-APK App Hub slice ahead of the full debloat catalog after the common capability and operation foundation exists.

## 13. Test and release strategy

- Physical coverage: representative Fire OS 7 and 8 devices, newer Android-based Fire OS versions when claimed, and an Android TV reference. Test low-memory/low-storage hardware. Vega is explicitly excluded from APK support.
- Functional cases: clean install, upgrade, no special grants, granted access, ADB rejection/disconnect, missing settings activities, no file picker, offline mode, wrong clock, network outage, and restart after a partially completed operation.
- Package operations: protected-package refusal, already-disabled state, stale recipe/build mismatch, dependency impact, partial batches, and restoration from the companion.
- Installer cases: corrupt/truncated APKs, signer conflict, incompatible ABI/API, storage exhaustion, user cancellation, repository-key change, and malicious metadata.
- UX cases: D-pad only, repeated Back, focus restoration, long names, Arabic/RTL, large fonts, and no focus traps.
- Privacy/network cases: no outbound requests with optional network features off; local log deletion; VPN conflict; DNS bypass status; broken list rollback; playback and login checks.
- Recovery cases: loss of power/network during operations; launcher crash; inaccessible stock launcher; recovery instructions available on the companion.
- Unit tests cover parsers, planners, capability decisions, state restoration, path boundaries, and signature/update policy. Instrumentation and physical tests prove OS behavior; mocks cannot certify Fire OS compatibility.

Establish p50/p95 cold-start, Home redirect, app-open, frame timing, memory, and idle-network baselines on selected hardware. Set initial budgets after measurement; a proposed gate is no more than 10% regression in launcher startup/memory with optional modules disabled. Publish measured results rather than untested speed claims.

Release internal -> device beta -> stable. Recipes are versioned independently but cannot execute new arbitrary commands. Keep signed known-good metadata and a local restore path. Update compatibility labels after firmware changes.

## 14. Decisions and recommended first scope

Proceed first with capability detection, a reversible operation foundation, own-storage cleanup, settings backup, a companion-backed debloater, and trusted-source APK installation. This combination addresses the requested freedom features while delivering value on unprivileged devices.

Defer universal ad-removal claims, permanent firmware-proof Home replacement, automatic private-data backups, arbitrary premium-unlock package indexing, universal reserved-button remapping, and Vega support. These either lack a reliable technical basis for this APK or introduce a different product scope.

Before implementation, record the exact primary test device/model/build, supported OS floor, preferred initial companion platform, and priority between debloating and app discovery. These are implementation inputs; they do not prevent evaluating this roadmap now.
