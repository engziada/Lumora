# Quick Menu refinement — 2026-11-09

- The particle lock is smaller, sits at the name’s top-right corner, bounces twice after formation and then rests.
- Quick Menu uses a 40dp circular icon matching Settings, with a tooltip on D-pad focus.
- The menu slides in and reveals an animated detail pane on each focused category. Right or OK enters its actions.
- Recent lists up to six installed recent apps; launching still uses the app-lock gate.
- Clear Memory shows total, used and free RAM, a usage bar and an explicit Clear action. Protected-app authorization still applies.
- Screenshot offers immediate capture or a quiet three-second timer; the overlay closes before capture.
- Power offers Restart and Power off through the device’s system power menu. These are system handoffs: the user must select the action there, and devices may omit Power off. Lumora does not have privileged reboot/shutdown permissions.
- Wi-Fi shows the active Wi-Fi name when Android allows it, link speed when available, and a settings button. Hidden names and non-Wi-Fi connections have explicit fallback text. Link speed is labeled separately from internet speed.

Validation: 89 JVM tests passed; debug and instrumentation APKs assembled; lint completed with zero errors. The TV emulator passed 53 assertions including locked-name formation, all eight locked app-menu gates, key rejection/cancellation/success, one-action authorization, missing key data, header placement, both Quick Menu entry points and focus-driven detail panes. Screenshots are in `screenshots/quick-*.png` and `screenshots/locked-name.png`.

System screenshot capture, memory clearing, and power actions were not executed on the user’s Fire TV. Screenshots and Power still need the quick-actions accessibility service; SSID availability depends on Android privacy restrictions.
