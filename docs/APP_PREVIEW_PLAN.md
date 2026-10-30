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
