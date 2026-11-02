# Lumora — distribution options

Where Lumora can be published, what each channel costs, and which ones are worth the work today.
Current version: v5.4.x, release-signed APK, minSdk 26, targetSdk 34, package `com.firestick.aura.launcher`.

> **Cost legend:** the "Cost" line is only what the store itself charges to publish. It excludes
> your own hosting, code-signing infrastructure and developer time.

---

## Recommended today

### 1. GitHub Releases (primary — runs automatically already)

- **Cost:** free. Unlimited releases; no registration, no listing fee.
- **How it works today:** `.github/workflows/android-build.yml` builds and publishes every push to
  `main` as a release with a signed APK plus `update.json` (the feed the in-app updater consumes),
  and keeps the five newest builds. Beta/rc version names are published as pre-releases, so the
  in-app stable/beta update channels (Settings › Tools) work against the same feed.
- **Effort:** zero — it is already running.
- **Limits:** users sideload by downloading the APK (or pressing "check for updates" in the app).
  No store discovery. No automatic updates outside the app's own checker.

### 2. Own website — zakaa-ai.net (primary, together with GitHub)

- **Cost:** no store fee; you already pay for hosting the domain. Serving the APK is a few MB of
  traffic per download — negligible at this project's scale.
- **How it works:** host the latest release APK + SHA-256 next to the GitHub release (or link
  straight to GitHub) with a short install guide: enable ADB apps from unknown sources / install
  via adb, then open. Keep `update.json` on GitHub as the single source of truth — never host a
  divergent APK copy with the same package name.
- **Effort:** one page. Do this before any store listing.

---

## Worth adding later

### 3. F-Droid (secondary — free, some engineering)

- **Cost:** free. No registration fee, no revenue share. F-Droid builds everything from source
  on its own infrastructure.
- **Requirements that shape the codebase:**
  - The full app must be FOSS and build from a clean checkout — the app itself is fine, but the
    release signing key stays private (F-Droid signs with its own key; version signing differs
    from your GitHub APKs, so in-app self-update must treat F-Droid builds as a separate
    distribution. Plan: keep the updater disabled when installed from F-Droid).
  - Metadata file (`metadata/com.firestick.aura.launcher.yml`) in the F-Droid data repo.
  - No non-free dependencies/trackers; filesystem and network behaviour audited at review.
- **Value:** automatic updates on device without sideloading, real discovery, reputation.
  Android TV / Fire TV clients exist (F-Droid APK installs fine on Fire OS; the F-Droid TV client
  is usable with the D-pad).
- **Effort:** medium (package metadata + source-cleanliness check). Recommended as the first store.

### 4. Aptoide TV (optional extra channel)

- **Cost:** free to publish. No listing fee; Aptoide monetizes via its own store services and
  optional payment plugins, revenue share only if you sell through it.
- **Value:** Aptoide TV is the de-facto alternative app store on Fire TV/Android TV where Google
  Play is absent; it reaches users who will not sideload.
- **Effort:** small (store listing, APK upload through Aptoide's console). Low risk of policy
  conflicts: home-screen launchers are allowed there.
- **Caution:** updates served by Aptoide may sit behind Aptoide's own update flow; keep the
  self-updater detecting the install source.

### 5. Google Play — Android TV track (secondary; constrained)

- **Cost:** one-time **US $25** developer registration for the full account; per-app none.
- **Value & limits:** reaches real Android TV devices (Sony, TCL, Chromecast TV, …), **not** Fire
  TV, which has no Play Store. Android TV submission requires: 512×512 store icon, TV banner,
  16:9 screenshots, declaring `uses-feature android.software.leanback`, D-pad-only operation
  (Lumora already navigates by D-pad — the Fire-stick UI rules map over well).
- **Relevance to 2027 certification:** owning a Google Android Developer Console registration is
  what matters for **certified Android TV (GMS)** distribution in 2027 — it is not required for
  Fire OS distribution. Register early if that roadmap holds.
- **Effort:** medium (listing assets, TV quality checklist, release management per format rules).

---

## Not worth it (do not invest)

### 6. Amazon Appstore — rejected (documented decision)

- **Cost:** none to publish (Amazon's developer program has no annual fee) — but **this channel is
  off the roadmap**.
- **Why:** Amazon's Device and System Abuse Policy explicitly prohibits apps that override or
  replace the native device user experience, including home-screen launchers and anything that
  alters the Fire TV home flow. Lumora is exactly that. Submitting would risk rejection at
  review, takedown after review, or worse: an account flag that also blocks hypothetical future
  non-launcher apps from the same account.
- **Fire TV publishing overhead (if the policy ever changes):** two Fire TV icons (512×512 PNG
  and 1280×720 banner), device targeting for the Fire TV form factor, privacy/data-safety labels
  (GDPR-style questions), an age/content rating questionnaire — roughly a day of paperwork on top
  of matching the store's catalog rules.
- **Re-check trigger:** revisit only if Amazon visibly relaxes the policy (track the developer
  policy page; search for "launcher" wording changes).

### 7. APK mirror sites (APKPure, APKCombo, etc.) — avoid

- **Cost:** free to *appear* on them, but you do not control the listing.
- **Why avoid:** unsigned/uncertified re-hosting strips provenance; users cannot verify SHA-256;
  the in-app updater rejects APKs not signed with your release key anyway (by design). Point
  users at GitHub or the website instead. Report impostor listings when found.

---

## Decision summary

| Channel | Publishing cost | Status | Reach |
|---|---|---|---|
| GitHub Releases | free | **running today** | everyone who can sideload |
| Own website | hosting only | next step | same, friendlier onboarding |
| F-Droid | free | planned | Android TV/Fire TV sideloasters, auto-updates |
| Aptoide TV | free | optional | non-Play TV stores |
| Google Play (TV) | $25 once | later; key for **certified Android TV 2027** | Android TV only |
| Amazon Appstore | free | **rejected — policy bans UX-replacing launchers** | — |
| APK mirrors | free | avoided | untrusted provenance |

**Single source of truth for updates stays the GitHub `update.json` feed**; every other channel is
either a mirror of the same APK+signature or a build that disables the in-app updater (F-Droid).
