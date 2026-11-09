# Recent apps, Wi-Fi and pane navigation — 2026-11-09

- Recent now displays up to six installed apps as a three-column grid with square app icons and names below them. Launching still uses the existing lock gate.
- Wi-Fi can request Android’s optional coarse/fine location permissions to reveal the SSID. The permission flow is initiated by an explicit button. If Location is off, a device Location settings action is offered; denied permissions can be managed in app settings. No location coordinates are read or stored.
- Replaced router link speed with an explicit internet download test to Cloudflare’s public measurement endpoint. It downloads at most 8 MiB, uses connect/read timeouts and a bounded transfer loop, supports cancellation, and reports a single-stream estimate in decimal Mbps. It never runs on focus; leaving the pane or closing the dialog cancels it.
- Settings and Quick Menu now contain Up/Down navigation within each pane. Right enters content; Left returns to the active menu item. Up/Down also reaches controls that share a horizontal row. Settings remembers its last focused control where still available. Setup falls back to a real focusable control rather than its introductory text. Quick Menu setup remains reachable at the bottom of the menu.

Validation:
- Debug APK and instrumentation APK assembled; 93 JVM tests passed, including download-unit tests for byte caps, cancellation, failed responses and Mbps conversion. Lint: 0 errors (142 warnings).
- TV emulator: 59 assertions passed across all 12 Settings sections, vertical boundaries, matching Left return, Recent icons/names, Quick Menu containment and explicit speed-test completion/error behavior.
- Visual verification: `screenshots/recent-grid.png` and `screenshots/wifi-download-result.png`. With emulator Wi-Fi and location access enabled, the panel displayed AndroidWifi and a measured 1.6 Mbps. The failure state was also exercised while the emulator was disconnected.
- Physical Fire TV permission dialogs and network throughput remain device-dependent.

References: Android WifiInfo SSID permissions https://developer.android.com/reference/android/net/wifi/WifiInfo ; Cloudflare measurement endpoint https://github.com/cloudflare/speedtest .
