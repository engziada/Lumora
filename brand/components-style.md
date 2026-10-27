# Components

| Component | Shape | Default | Focused |
| :--- | :--- | :--- | :--- |
| App tile (`item_app_card`) | 70dp plate, 20dp radius | `bg_icon_plate`, label `nova_text` 9sp | Scale 1.07, accent ring 2.5dp, lift 12dp |
| Featured card | 158 x 86dp, 18dp radius | Bronze-to-espresso gradient | Accent stroke 2dp, scale 1.04 |
| Poster card | 2:3, 12dp radius | Poster image, title below | Accent stroke 2dp, scale 1.06 |
| Chip | 32dp high, 16dp radius | Plate fill, `nova_stroke` border | Solid accent, `nova_text_on_accent` text |
| Option button | 44dp high, 14dp radius | `nova_surface_2` + 1dp stroke | `nova_card_hi` + 2dp accent stroke |
| Quick tile | 96 x 80dp, 16dp radius | Plate fill, icon + label | Accent stroke, icon tinted accent |
| Panel | 20dp radius | `glass_panel` + 1dp cream 15% stroke | — |
| Dialog | 24dp radius | Espresso gradient + amber 25% stroke | — |
| Bottom navigation | Full width, 54dp | `glass_nav` + amber hairline on top | Item gets accent stroke; selected item accent text |

## States

Every interactive element has default, focused, pressed (where relevant), disabled (40% alpha) and selected states. Lists show loading, empty and error messages in `Lumora.Body` text.

## Motion

140ms ease-out for focus scale; 700ms cross-fade for wallpapers. No bounce, overshoot or elastic easing.
