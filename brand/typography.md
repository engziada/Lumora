# Typography

**Family:** Poppins (SIL OFL 1.1) — Regular 400, Medium 500, SemiBold 600, bundled in `app/src/main/res/font/`.
Its geometric, round letterforms echo the LUMORA wordmark. The wordmark itself is drawn as vector strokes and is never typeset.

## TV type scale (10-foot, 1080p at 2.0x density)

Android TV renders at 960 x 540 dp. Sizes are fixed steps on a 1.25 ratio rather than fluid, because TV screens do not reflow.

| Role | Size | Weight | Tracking | Use |
| :--- | :--- | :--- | :--- | :--- |
| Display | 22sp | SemiBold | +0.04em | Greeting (`GOOD EVENING`) |
| Title | 15sp | SemiBold | +0.02em | Row titles, dialog titles |
| Subtitle | 12sp | Medium | 0 | Settings sections, poster titles |
| Body | 10sp | Regular | 0, line height 1.25 | Descriptions, summaries |
| Label | 9sp | Medium | +0.02em | App labels, buttons |
| Eyebrow | 8sp | Medium, all caps | +0.22em | Section labels, tagline |

## Vertical rhythm

Baseline grid of 4dp. Text blocks use margins in multiples of 4dp (4, 8, 12, 16, 24). Line height for body copy is 1.25 x size, rounded to the 4dp grid.

## Rules

- Use all caps only for the eyebrow role and the greeting.
- Never set body copy in `nova_text_muted` below 9sp.
- Do not use system fallbacks such as Roboto or generic sans-serif for UI text.
