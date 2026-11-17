# Components

| Component | Shape | Default | Focused |
| :--- | :--- | :--- | :--- |
| App tile (`item_app_card`) | 70dp plate, 20dp radius | `bg_icon_plate`, label `nova_text` 9sp | Scale 1.07, accent ring 2.5dp, lift 12dp |
| Featured card | 158 x 86dp, 18dp radius | Bronze-to-espresso gradient | Accent stroke 2dp, scale 1.04 |
| Poster card | 2:3, 12dp radius | Poster image, title below | Accent stroke 2dp, scale 1.06 |
| Chip | 32dp high, 16dp radius | Plate fill, `nova_stroke` border | Solid accent, `nova_text_on_accent` text |
| Option button | 44dp high, 14dp radius | `nova_surface_2` + 1dp stroke | `nova_card_hi` + 2dp accent stroke |
| Quick tile | 96 x 80dp, 16dp radius | Plate fill, icon + label | Accent stroke, icon tinted accent |
| Panel (`bg_panel`) | 24dp radius | `lumoraGlassPanel`, top sheen, 1dp `glass_edge` | — |
| Frosted surface (`ui/Frost`: nav dock, Settings rail and panels) | 24–26dp radius | Blurred wallpaper slice, glass fill, top sheen, 1dp `glass_edge` | — |
| Dialog (`ui/Glass`, `GlassMenu`) | 26dp radius | `bg_glass_panel` over a blurred snapshot of the screen | Rows: `bg_glass_row`, `lumoraGlassFocus` + 1.5dp accent stroke |
| Panel over video or other apps (`bg_dialog_glass`) | 24dp radius | Denser `lumoraGlassSheet`, sheen, `glass_edge` | — |
| Bottom navigation | Floating dock, 26dp radius | Frosted `lumoraGlassBar`, sheen, `glass_edge` | Item gets accent stroke; selected item accent text |
| Guide rail item (`item_guide_category`) | 34dp high, 14dp radius | Name 11sp, count 9sp, no fill | `lumoraGlassFocus` + 1.5dp accent stroke; selected: `nova_accent_soft` |
| Guide row (`item_guide_row`) | 12dp radius; 10dp cells | Logo, on-air and next cells on `lumoraGlassCard` | `glass_wash` + 2dp accent ring; logo and on-air cells lift to `lumoraGlassFocus` |
| Film card (`item_poster_card`) | 162 x 91dp (16:9), 12dp radius | Pluto TV poster (archive thumbnail as fallback) on `lumoraGlassCard`; title, then year · length · rating; resume bar | Accent ring, scale 1.06 |
| Rating tag (`ui/RatingBadge`) | Inline span, 4dp radius | Rating at 78% of the line's text size, bold; 1dp outline in the text colour at 70% | — |
| Subtitle style menu (`ui/SubtitleStyle`) | `GlassMenu` | One row per setting with its value; OK cycles the value and the subtitles behind update at once | Rows as Dialog |

With Reduce transparency on, frosted surfaces and dialogs draw only their opaque fill and edge.
In single-line titles, `RatingBadge.fit` shortens the title with an ellipsis so the tag is never cut off.

## States

Every interactive element has default, focused, pressed (where relevant), disabled (40% alpha) and selected states. Lists show loading, empty and error messages in `Lumora.Body` text.

## Motion

140ms ease-out for focus scale; 700ms cross-fade for wallpapers. No bounce, overshoot or elastic easing.
