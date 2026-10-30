# Home app preview prototype

Scope: an opt-in, offline demonstration of a non-interactive preview on the Home page.
This tests the visual interaction before integrating licensed catalogs or provider authorization.
Samples are bundled editorial fixtures, not live listings, rankings, availability guarantees, or personal recommendations.

- Settings > Appearance > App preview demo enables the experiment (off by default).
- D-pad focus on YouTube, Prime Video, or Netflix waits 600 ms before revealing a panel.
- Three sample title cards rotate every six seconds. The panel never takes input or focus.
- Leaving the app strip, switching tabs, disabling previews, or pausing hides the panel and cancels callbacks.
- Locked apps do not reveal previews. OK continues through the existing app launch/lock flow.
- No account storage, new permissions, API keys, network hosts, or database changes.

Implementation: package-to-provider mapping and immutable fixtures, a lifecycle-bound panel controller,
Home focus integration, a preference and Settings control, local vector art, and resource strings.

Validation: provider/package mapping tests; unsupported packages and fixture bounds;
debug APK build and lint; emulator checks for dwell, rotation, cancellation, opt-out,
focus retention, and the existing launch behavior. Physical Fire TV validation remains separate.

## Validation result — 2026-10-30

- JDK 17: `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` passed.
- 68 unit tests passed, including three new provider selection and carousel tests.
- Lint: zero errors, 98 warnings (including small text sizes consistent with the existing TV layout).
- Television_1080p emulator: 21 UI assertions passed. Covered initial dwell, service switching,
  rotation, rapid traversal, focus retention, full disclaimer visibility, leaving Home,
  tab switching, locked-app suppression, opt-out, and opening the installed YouTube app with OK.
- Netflix and Prime used injected test tiles; no provider accounts or installations were used for them.
- Reviewed Netflix and YouTube screenshots at 1920 × 1080. Screenshot files and a named test APK
  are available locally under `build-out/preview-review/` (ignored build artifacts).
- No physical Fire TV, account authorization, live catalog feed, or personal recommendations tested.


## Real video feasibility revision — local only

User clarified the intended middle-right video window above the ribbon. Replaced sample cards
with the official YouTube embedded player. Public videos, muted, 15 seconds of playback per item;
600 ms dwell, unload on focus loss or activity pause. No personalized account data.

Physical device: AFTKM/karat, Fire OS 8.1.8.5 (RS8185/3891), Android 11/API 30, Amazon WebView 150.
Installed the separate debug package com.firestick.aura.launcher.localtest, preserving release 1159.
Observed Netflix trailer playback advancing from b9EkMc79ZSU to mnd7sFt5c3A with muted=true and
increasing playback time. Switched to YouTube and observed aqz-KE-bpKQ playing muted. Moving
focus down to navigation hid the window and stopped periodic playback logs. Screenshot reviewed
on the physical TV output; layout fits above the ribbon. Prime is not installed on this device,
so its integration remains untested physically. Video identifiers and publishers checked through
YouTube oEmbed. No push or release publication authorized until user reviews the local build.


## Key-free refreshed feeds revision

User chose public recent-upload feeds without API keys; country comes from existing TV listings.
Added public YouTube Atom loading for official Netflix, Prime Video and YouTube channels. Prefer
trailer/teaser/sneak-peek entries for Netflix/Prime, otherwise use recent uploads. Cache for one
hour, with a maximum one-day offline fallback. Validate and deduplicate video IDs. Ignore stale
async callbacks after focus loss. Skip playback failures and stop after all entries fail.
No hardcoded video-ID fallback remains. Added actual Fire TV Prime package com.amazon.firebat.

Removed launcher header/footer; only transient loading/error overlays remain. Global feeds cannot
provide country-specific rankings or availability; Settings and README disclose that limitation.
Existing country preference scopes the cache and Arabic/English player UI. The test package has
its own TV listings preferences (currently default US); the release preferences are untouched.

Five new parser tests cover trailer selection, upload fallback, ID validation/deduplication,
empty results and malformed input. Physical Netflix feed returned eight videos and began muted
playback of a newly fetched ID rather than any previous fixed sample. Further physical results
are recorded in the chat. No push or release publication until user approves.

Physical follow-up: all three feeds fetched and played muted videos on AFTKM. YouTube returned
12 IDs and played gpit4lmDl94; Prime played NaeeNnuBXzY. Updated ordering to trailers first,
then remaining recent uploads, so a channel with only one trailer still cycles through different
videos. Maximum 12 IDs per feed. Full local validation: 73 tests passed; lint zero errors,
108 warnings. This initial scope remains local-only and installed alongside release 1159.

## Widescreen filter and frame

User requested excluding Shorts/portrait videos and adding a frame. oEmbed returned landscape
player sizes for portrait footage, so it is unsuitable. Public watch-page video metadata
distinguishes the observed Prime Short (1080x1920) from its trailer (1920x1080). Accept only
aspect ratios 1.65–1.90; skip unknown dimensions; cache decisions for one day. Playback remains
through the official embedded player. Added an amber 2dp border with 3dp inset. Black bars
encoded into a landscape video remain a limitation. No push until user approval.

Physical validation: first metadata requests using the generic launcher user agent returned no
usable dimensions. A browser-style request corrected this; versioned the shape cache to discard
those initial rejections. Netflix retained 11 videos; Prime retained four of 12. Confirmed the
previous portrait video _Nndjr8n0vY is excluded, while NaeeNnuBXzY and XxN1Quuq5U8 play muted
and advance automatically. Amber frame visually verified on AFTKM. Updated test APK installed
alongside release. Unit tests and lint passed; no push.
