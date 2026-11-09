# Locked apps and Quick Menu — 2026-11-08

- Formation uses elapsed time so dropped frames cannot indefinitely stretch the intro; newly revealed panels wait for text targets to be measured.
- Particle mode now forms locked app names and an additional dotted padlock. The name remains readable while the lock orbits it. Video previews remain blocked for locked apps; turning the panel off still disables it.
- Every app context-menu action verifies the key before executing: open, move, add/remove Home, change category, unlock, hide, app info and uninstall. Authorization applies to one action and does not remove the app lock.
- Unhiding a locked app, resetting hidden apps when locked apps are included, and bulk memory cleanup also verify the key. Missing key data fails closed.
- Quick Menu now sits beside Settings in the top header. Its panel uses compact 44dp rows, smaller icons and a visible focus border. Play/Pause opens it while on the launcher; Back dismisses it. Menu retains app actions and the assistant hold shortcut.

## Validation

The offline device regression is selected with `-e lockQuick true` on the existing `PreviewInstrumentation` runner. It checks locked particle visibility, all context-menu gates, incorrect and canceled keys, one-action authorization, missing key data, header placement, and both Quick Menu entry points. Screenshots are saved in the debug app's external files directory.

Build output uses a temporary Gradle init script directing output to `build-lock-validation/` because the existing `build-out/` generated files reject writes on this machine. No signing or release configuration is changed.

Build validation on 2026-11-09: debug APK and instrumentation APK assembled; all 89 JVM tests passed; Android lint completed with 0 errors and 133 warnings.

TV emulator validation passed: 48 assertions covering formed locked names, all eight app-menu gates, canceled/wrong/correct keys, fresh authorization, missing key data, header position, Play/Pause and button entry, and five visible quick actions. Screenshots: `screenshots/locked-name.png`, `screenshots/locked-orbit.png`, `screenshots/quick-menu.png`.
